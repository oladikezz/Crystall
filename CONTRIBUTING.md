# Contributing to Crystall

Thank you for your interest in contributing to **Crystall**!

## Development Guidelines

1. **Performance & Memory**:
   - Crystall is designed for extreme concurrency and low allocation overhead.
   - Avoid boxing/unboxing on tick loops. Use primitive collections (`Long2ObjectOpenHashMap`, `IntArrayList`, etc.) whenever possible.
   - Use reusable ring buffers and object pools for high-frequency operations.

2. **Code Style**:
   - Follow standard Java/Kotlin formatting enforced by `.editorconfig`.
   - Prefer descriptive method names and clear javadoc comments for core engine systems.

3. **Testing**:
   - Run tests via Gradle wrapper: `./gradlew test`.
   - Ensure all unit tests pass before submitting pull requests.
