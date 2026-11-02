---
type: regex
target: { source: file, path: migration.sql }
pattern: 'timestamptz|timestamp with time zone'
flags: i
---
