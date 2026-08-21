# SM_Help

Replaces `/help` (and `/?`) with text from `resources/config.yml`.

## Features
- Config-driven help text
- MiniMessage support
- Legacy `&` color codes support
- `&[MAIN]` and `&[SECONDARY]` theme placeholders
- Multiline support via YAML block text or line list

## Config keys
- `commands.help.enabled`
- `help.text` (string / block text / list)
- `help.lines` (fallback list)

