# LedgerLine

LedgerLine is an Android personal-finance tracker focused on fast expense entry, receipt-level grouping, flexible categories, and spreadsheet compatibility.

## Product decisions

- App name: LedgerLine
- Android: Kotlin + Jetpack Compose
- Local-first storage; cloud backup/sync is planned as a separate layer
- Quick entry and detailed entry
- Purchases can contain multiple expense lines. Purchase-level date and retailer are shared; each line has its own amount, category, secondary category, necessity, details, and notes.
- CSV import/export uses the existing spreadsheet columns, in this exact order:
  `Date, Amount, Person, Category, Secondary-Category, Necessity, Retailer, Details, More Information`
- `Person` is retained for imported historical rows and CSV round-tripping, but is not part of the new-entry UI.

## Development

Open this repository in Android Studio, or use the GitHub Actions workflow to build a debug APK.

## Data and privacy

Never commit personal transaction exports, bank statements, Firebase credentials, or `google-services.json`. The repository is currently public; change its visibility to private in GitHub settings if you prefer the source code to be private.
