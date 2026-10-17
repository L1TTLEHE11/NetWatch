# Contributing to NetWatch

Thanks for helping! Bug reports, ideas, translations and pull requests are all welcome.

## Bugs and ideas

Use [issues](https://github.com/L1TTLEHE11/NetWatch/issues/new/choose). For a bug, please include the app version, Android version, device model and steps to reproduce. A text or PDF report from “More → Report” helps a lot.

## Translating

All UI texts live in Android string resources:

| File | Language |
|---|---|
| `app/src/main/res/values/strings.xml` | English — the source and the fallback |
| `app/src/main/res/values-ru/strings.xml` | Russian |

To add a language (example: German, `de`):

1. Copy `values/strings.xml` to `values-de/strings.xml` and translate the text between the quotes. Keep the keys (`name="…"`) unchanged.
2. Keep placeholders exactly as they are: `%1$s`, `%2$s`, `%d`, `%%` and `\n`. Their order in the sentence may change.
3. Keep strings short — many of them are buttons and labels on a phone screen.
4. Add the language tag to `Lang.supported` in `app/src/main/java/com/l1ttlehe11/netwatch/Lang.kt`, to `app/src/main/res/xml/locales_config.xml`, and a chip to `LanguageTile()` in `UiHome.kt`.
5. Run `./gradlew testDebugUnitTest --tests '*LangTest*'` — it checks that every key exists in every language and that placeholders match.

Not a developer? Just send the translated `strings.xml` in an issue, and it will be added.

## Code

- Kotlin + Jetpack Compose, no third-party networking libraries.
- Never hard-code UI text in Kotlin: add a key to both `strings.xml` files and use `tr(R.string.key)` (or `stringResource` in Compose).
- Run `./gradlew testDebugUnitTest` before opening a pull request.
- By contributing you agree that your work is published under [GPL-3.0](LICENSE).
