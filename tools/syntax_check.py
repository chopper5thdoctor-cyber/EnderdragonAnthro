#!/usr/bin/env python3
"""Catch the two build breaks that can be caught without Minecraft on disk.

Fabric's maven is unreachable from this machine, so javac cannot resolve a
single vanilla class and buries everything under thousands of "cannot find
symbol". The obvious workaround — drop every line containing that phrase — is
what let six stale field references ship: `shade.awayTicks` reads exactly like
`particle.xd`, and only one of them is our fault.

There is no way to tell those apart from javac's output alone, because the
second is a *cascade*: PurpleHeartParticle extends a class we cannot see, so
none of its inherited members resolve either. So this checks two things it can
actually be sure about.

  1. SYNTAX. javac's parse errors — a missing brace, a stray semicolon — need
     no classpath at all, so they are trustworthy and are reported verbatim.

  2. OUR OWN MEMBERS. For every type this repository declares whose ancestry
     is entirely ours (so its full member surface is known), every `x.member`
     where x was declared with that type is checked against it. That is
     precisely the shape of the six that got through.

A type that extends anything vanilla is skipped rather than guessed at — the
whole point is to never report something we cannot prove.

    python3 tools/syntax_check.py
"""

import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(HERE, "src/main/java")

# javac phrases that come out of the parser, before any name resolution runs.
# These are sound with an empty classpath; everything else is not.
SYNTAX = (
    "expected", "illegal start of", "reached end of file while parsing",
    "not a statement", "unclosed string literal", "unclosed character literal",
    "unclosed comment", "illegal character", "invalid method declaration",
    "duplicate class", "should be declared in a file named",
    "illegal combination of modifiers", "repeated modifier",
    "variable is already defined", "is already defined in",
)

OBJECT_MEMBERS = {
    "equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait",
    "clone", "finalize",
}
ENUM_MEMBERS = {
    "values", "valueOf", "ordinal", "name", "compareTo", "getDeclaringClass",
    "describeConstable",
}
# Words that read like a variable but never are.
NOT_A_VAR = {"new", "return", "this", "super", "case", "instanceof", "final"}

#: A `Type name` declaration, capturing the type's head and the name.
#:
#: The head is enough: what this is for is noticing that one identifier is
#: declared as two different things in a file, and `Set` differing from `Shade`
#: settles that without parsing `Set<Map.Entry<K, V>>`. The optional generic and
#: array parts are skipped rather than captured, so `List<Foo> bar` and
#: `String[] bar` both land as ("List", "bar") and ("String", "bar").
#:
#: The array brackets ARE captured, and have to be: `DragonIntent[] all` makes
#: `all` an array, not a DragonIntent, and `all.length` is the array's own field
#: rather than a missing member. Keeping "[]" in the kind means such a name
#: never matches a bare type and is simply left alone.
DECLARATION = re.compile(
    r"(?<![\w.])([A-Z]\w*)(?:\s*<[^;=(){}]*>)?((?:\s*\[\s*\])*)\s+([a-z]\w*)\s*[;=,):]")

#: A lambda's parameters: `x -> ...` and `(x, y) -> ...`.
#:
#: Their types are inferred from the functional interface and are nowhere in
#: the text, so the checker cannot know them. It matters because short names
#: get reused: DragonMinions has `for (Shade s : court)` in one method and
#: `withStyle(s -> s.withColor(tone))` in another, and without this the Style
#: is read as a Shade and every call on it is reported missing.
LAMBDA_ONE = re.compile(r"(?<![\w.])([a-z]\w*)\s*->")
LAMBDA_MANY = re.compile(r"\(([^()]*)\)\s*->")

BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")
STRING = re.compile(r'"(?:\\.|[^"\\])*"')
CHAR = re.compile(r"'(?:\\.|[^'\\])*'")

TYPE_DECL = re.compile(
    r"\b(class|interface|enum|record)\s+(\w+)\s*"
    r"(?:<[^{]*?>)?\s*"                       # type parameters
    r"(\([^)]*\))?"                           # record components
    r"([^{;]*)"                               # extends / implements
)


def strip(src):
    """Blank out comments and literals so they cannot be mistaken for code."""
    src = BLOCK_COMMENT.sub(lambda m: re.sub(r"[^\n]", " ", m.group(0)), src)
    src = LINE_COMMENT.sub("", src)
    src = STRING.sub('""', src)
    src = CHAR.sub("' '", src)
    # Imports are dotted names, not member access — `...client.model.PlayerModel`
    # otherwise reads as a field `PlayerModel` on a variable called `model`.
    src = re.sub(r"^\s*(?:import|package)\b[^\n]*", "", src, flags=re.M)
    return src


class Type:
    def __init__(self, name, kind):
        self.name = name
        self.kind = kind
        self.supers = []
        self.members = set()
        self.file = ""


def scan_types(path, src):
    """Every type declared in one file, with its members.

    Nesting is resolved by brace depth: a member belongs to the innermost type
    whose body we are inside. Good enough for this codebase, which nests one
    level of plain static classes and records.
    """
    found = {}
    stack = []          # (Type, depth at which its body opened)
    depth = 0
    pending = None      # a type header seen, waiting for its '{'

    for match in re.finditer(r"[{}]|" + TYPE_DECL.pattern + r"|"
                             r"^[ \t]*(?:(?:public|private|protected|static|final|"
                             r"abstract|transient|volatile|synchronized|native|"
                             r"default|strictfp|sealed|non-sealed)\s+)*"
                             r"(?:<[^>]{0,120}>\s*)?"
                             r"([\w.$]+(?:<[^;=(){}]{0,200}>)?(?:\[\])*)\s+"
                             r"(\w+)\s*(?=[;=(])",
                             src, re.M):
        token = match.group(0)
        if token == "{":
            depth += 1
            if pending is not None:
                stack.append((pending, depth))
                pending = None
            continue
        if token == "}":
            if stack and stack[-1][1] == depth:
                stack.pop()
            depth -= 1
            continue

        if match.group(1) in ("class", "interface", "enum", "record"):
            kind, name = match.group(1), match.group(2)
            entry = Type(name, kind)
            entry.file = path
            if match.group(3):                       # record components
                for part in match.group(3)[1:-1].split(","):
                    words = re.findall(r"\w+", part)
                    if words:
                        entry.members.add(words[-1])
                        entry.members.add(words[-1])
            tail = match.group(4) or ""
            entry.supers = [w for w in re.findall(r"\b([A-Z]\w*)", tail)]
            found[name] = entry
            pending = entry
            continue

        # a field or method belonging to whatever type we are inside
        member = match.group(6)
        if member and stack and member not in NOT_A_VAR:
            stack[-1][0].members.add(member)

    # enum constants: the comma-separated identifiers before the first ';'
    for entry in found.values():
        if entry.kind != "enum":
            continue
        body = re.search(r"\benum\s+" + entry.name + r"\b[^{]*\{(.*?)(?:;|\})",
                         src, re.S)
        if body:
            for constant in re.finditer(r"\b([A-Z][A-Z0-9_]*)\s*(?:\(|,|;|\})",
                                        body.group(1)):
                entry.members.add(constant.group(1))
    return found


def surface(entry, types, seen=None):
    """Everything reachable on a type, or None if any ancestor is not ours."""
    seen = seen or set()
    if entry.name in seen:
        return set()
    seen.add(entry.name)
    members = set(entry.members) | OBJECT_MEMBERS
    if entry.kind == "enum":
        members |= ENUM_MEMBERS
    for parent in entry.supers:
        if parent not in types:
            return None                  # vanilla, or generic — cannot know
        inherited = surface(types[parent], types, seen)
        if inherited is None:
            return None
        members |= inherited
    return members


def check_members(files, types):
    """Report `x.member` where x's type is fully ours and has no such member."""
    known = {}
    for name, entry in types.items():
        members = surface(entry, types)
        if members is not None:
            known[name] = members

    problems = []
    for path, src in files.items():
        lines = src.splitlines()
        # Every `Type name` declaration in the file, keyed by the name.
        #
        # An identifier only counts as one of our types if EVERY declaration of
        # it in the file agrees. This used to subtract names that carried
        # another of OUR types and stop there, which left every foreign type
        # invisible -- so in a file with a nested `Shade` record, a parameter
        # `EnderMan shade` was read as a Shade and each of its six EnderMan
        # calls was reported as a missing member. Ten false positives, and
        # nothing real among them: the check was red for twenty-three pushes
        # and had stopped being able to say anything at all.
        declared = {}
        for kind, arrays, var in DECLARATION.findall(src):
            declared.setdefault(var, set()).add(kind + "".join(arrays.split()))

        inferred = set(LAMBDA_ONE.findall(src))
        for group in LAMBDA_MANY.findall(src):
            for piece in group.split(","):
                piece = piece.strip()
                if re.fullmatch(r"[a-z]\w*", piece):
                    inferred.add(piece)

        for name, members in known.items():
            vars_ = {var for var, kinds in declared.items() if kinds == {name}}
            vars_ -= NOT_A_VAR
            vars_ -= inferred
            if not vars_:
                continue
            for var in vars_:
                # `(?<![\w.])` so a dotted path like a.b.model.Thing is not read
                # as a field `Thing` on a local called `model`.
                for hit in re.finditer(r"(?<![\w.])" + var + r"\.(\w+)", src):
                    used = hit.group(1)
                    if used in members:
                        continue
                    line = src[:hit.start()].count("\n") + 1
                    problems.append(
                        f"{path}:{line}: {name}.{used} does not exist\n"
                        f"    {lines[line - 1].strip()}")
    return problems


def check_syntax():
    sources = [os.path.join(r, f) for r, _, fs in os.walk(SRC)
               for f in fs if f.endswith(".java")]
    with tempfile.TemporaryDirectory() as out:
        proc = subprocess.run(
            ["javac", "-proc:none", "-nowarn", "-Xmaxerrs", "100000",
             "-d", out] + sources,
            capture_output=True, text=True)

    blocks, current = [], []
    for line in proc.stderr.splitlines():
        if re.match(r"^\S.*:\d+: (error|warning):", line):
            if current:
                blocks.append(current)
            current = [line]
        elif current:
            current.append(line)
    if current:
        blocks.append(current)

    return [("\n".join(b), len(blocks)) for b in blocks
            if "error:" in b[0] and any(p in b[0] for p in SYNTAX)]


def main():
    files = {}
    for root, _, names in os.walk(SRC):
        for name in names:
            if name.endswith(".java"):
                path = os.path.join(root, name)
                files[os.path.relpath(path, HERE)] = strip(open(path).read())

    types = {}
    for path, src in files.items():
        types.update(scan_types(path, src))

    syntax = check_syntax()
    for text, _ in syntax:
        print(text)

    members = check_members(files, types)
    for text in members:
        print(text)

    bad = len(syntax) + len(members)
    if bad:
        sys.exit(f"\nFAIL: {len(syntax)} syntax error(s), "
                 f"{len(members)} bad member reference(s)")
    print(f"PASS: {len(files)} files parse, and every member used on the "
          f"{sum(1 for t in types.values() if surface(t, types) is not None)} "
          f"types we fully own exists")


if __name__ == "__main__":
    main()
