# SM_Announces

Console-only alert module for SMPS.

## Command

`/consolealert [nickname] [type] "[text]" [sound|none]`
`/consolealertlog [on|off|toggle|status]`

- `nickname` - online player name
- `type` - `chat`, `title`, `all`
- `text` - supports MiniMessage tags and SMPS color placeholders/codes
- `sound` - optional namespaced sound key (default: `minecraft:block.note_block.chime`)
- `none` - disable sound for this alert

If you omit quotes, a trailing value like `minecraft:block.note_block.pling` or `none` is treated as the optional sound token.

## Examples

```text
/consolealert Steve chat "&[MAIN]Server restart in <yellow>5 minutes</yellow>"
/consolealert Steve title "<gradient:#ff7aa2:#ffc0d9>Maintenance</gradient>" minecraft:block.note_block.pling
/consolealert Steve all "<click:open_url:'https://example.com'><hover:show_text:'<yellow>Open website</yellow>'>Visit site</hover></click>" none
```

## Notes

- Command is rejected for players and allowed for console.
- Plain URLs like `https://example.com` become clickable automatically.
- Title timings are configurable in `config.yml`.
- Default sound, volume, and pitch are configurable in `config.yml`.
- Console feedback for each sent alert can be toggled with `/consolealertlog`.

