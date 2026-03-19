{%
laika.title = Syntax reference
%}

# Syntax reference

Cornichon uses several special notation patterns within step arguments:

- [Placeholders](syntax/placeholders.md) — `<key>` expressions resolved from the session or built-in generators
- [JSON Path](syntax/json-path.md) — `$.field[0].nested` expressions for navigating JSON structures
- [JSON Matchers](syntax/json-matchers.md) — `*any-string*` patterns for asserting on field types without exact values
- [Data Tables](syntax/data-tables.md) — `| col | val |` pipe-delimited tabular format for assertions and data-driven tests
