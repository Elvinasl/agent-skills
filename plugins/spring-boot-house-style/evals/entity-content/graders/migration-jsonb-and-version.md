---
type: regex
target: { source: file, path: migration.sql }
pattern: '(?=[\s\S]*jsonb)(?=[\s\S]*version)'
flags: i
---
