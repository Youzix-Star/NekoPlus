import sys, re

def scan(path, verbose=False):
    s = open(path, encoding='utf-8').read()
    i, n, depth, line = 0, len(s), 0, 1
    stack = []
    while i < n:
        c = s[i]
        if c == '\n':
            line += 1; i += 1; continue
        if c == '/' and s[i:i+2] == '//':
            while i < n and s[i] != '\n': i += 1
            continue
        if c == '/' and s[i:i+2] == '/*':
            i += 2
            while i + 1 < n and not (s[i] == '*' and s[i+1] == '/'):
                if s[i] == '\n': line += 1
                i += 1
            i += 2; continue
        if s[i:i+3] == '"""':
            i += 3
            while i + 2 < n and s[i:i+3] != '"""':
                if s[i] == '\n': line += 1
                i += 1
            i += 3; continue
        if c == '`':
            i += 1
            while i < n and s[i] != '`':
                if s[i] == '\n': line += 1
                i += 1
            i += 1; continue
        if c == '"':
            i += 1
            while i < n:
                if s[i] == '\\': i += 2; continue
                if s[i] == '"': i += 1; break
                if s[i] == '\n': line += 1
                i += 1
            continue
        if c == "'":
            i += 1
            while i < n:
                if s[i] == '\\': i += 2; continue
                if s[i] == "'": i += 1; break
                i += 1
            continue
        if c == '{':
            depth += 1; stack.append(line)
        elif c == '}':
            depth -= 1
            if stack: stack.pop()
            if depth < 0:
                print(f"{path.split('/')[-1]}: EXTRA }} at line {line}")
                return False
        i += 1
    if depth != 0:
        print(f"{path.split('/')[-1]}: UNBALANCED depth={depth} unclosed={stack[:6]}")
        return False
    return True

bad = 0
for p in sys.argv[1:]:
    if not scan(p): bad += 1
print("OK" if bad == 0 else f"{bad} file(s) unbalanced")
