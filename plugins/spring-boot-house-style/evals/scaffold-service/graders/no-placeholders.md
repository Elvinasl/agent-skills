---
type: regex
target: { source: file, path: build.gradle }
pattern: '__[A-Z_]+__|>>>mod:|<<<mod:'
match: not_contains
---
