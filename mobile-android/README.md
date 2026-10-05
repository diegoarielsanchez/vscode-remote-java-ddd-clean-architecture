# MedRep Android client

Kotlin / Jetpack Compose app for medical sales reps. It talks to the backend **only through the API gateway** (`:8080`) and covers:

| Feature | Backend | What the app does |
|---|---|---|
| Sign in | identity-service `/auth/login` | Stores the JWT and attaches it as `Bearer` to every call |
| Visits | visit-service `/api/v1/visit/**` | Paged list, detail, create/edit, upload product promo attachments |
| Visit plans | visit-service `/api/v1/visitplan/**` | Paged list, create/edit (date + time, must be in the future) |
| Settlements | settlement-service `/api/v1/settlement/**` | Paged list, create/edit header, add invoice with file, remove invoice |
| HCP picker | healthcare-prof-service `/api/v1/healthcareprof/**` | Searchable bottom sheet with specialty filter chips; id → name in lists |
| MSR picker | medical-sales-rep-service `/api/v1/medicalsalesrep/**` | Searchable bottom sheet; id → name in lists |

## Architecture (MVVM)

```
View (Compose)  ──observes StateFlow──▶  ViewModel  ──▶  Repository  ──▶  Retrofit API  ──▶  Gateway
      ▲                                     │                 │
      └────────── user intents ─────────────┘                 └─ publishes DataChange ─▶ list/detail VMs refresh
```

```
app/src/main/java/com/das/mobile/
├── core/
│   ├── network/   Retrofit/OkHttp, AuthInterceptor (JWT + 401 → logout), ApiError, SafeApiCall, BigDecimalSerializer
│   ├── session/   SessionStore (DataStore) → SessionState drives login vs main graph
│   ├── data/      ChangeNotifier (repositories announce mutations)
│   ├── files/     DocumentReader (SAF → UploadFile, extension/size checks)
│   ├── ui/        theme, UiState, form fields, PagedList(+ViewModel), generic EntityPicker
│   └── util/      backend date formats, UI formatting
├── feature/
│   ├── auth/        login
│   ├── hcp/         HCP directory repository + HcpPickerField
│   ├── msr/         MSR directory repository + MsrPickerField
│   ├── visit/       visits + visit plans
│   └── settlement/  settlements + invoices
└── navigation/    type-safe routes, AppRoot, HomeScreen (3 tabs)
```

Stack: Compose + Material 3, Hilt, Navigation Compose (type-safe routes), Retrofit + kotlinx.serialization, OkHttp, DataStore. minSdk 26.

## Running

1. Start the backend (gateway on `localhost:8080`), e.g. `./start-all-services.sh` from the repo root.
2. Open `mobile-android/` in Android Studio (Ladybug or newer) and run the `app` configuration on an emulator.

The emulator reaches the host's gateway at `http://10.0.2.2:8080/` (the default). To use another URL:

```bash
./gradlew :app:installDebug -PapiBaseUrl=http://192.168.1.20:8080/
```

For a physical device over plain HTTP also add its host to `app/src/debug/res/xml/network_security_config.xml`. Release builds allow HTTPS only.

When the backend runs in the dev container on WSL, make sure port 8080 is forwarded to Windows.

Tests: `./gradlew :app:testDebugUnitTest` (error parsing, multipart/DELETE contracts, directory caching via MockWebServer).

## Backend quirks the app works around

These come from the current backend code; fixing them server-side would simplify the client.

- **List endpoints are `POST …/list?page=&pageSize=`** with no body; pages start at 1.
- **Empty lists are errors:** visit, visit-plan, HCP and MSR list use cases throw `400 "… not found"` instead of returning `[]` (also when paging past the end). See `recoverEmptyList()`.
- **HCP/MSR name search is exact-match**, and an empty filter returns everyone unpaginated. The pickers therefore load the directory once per session and filter on the device. For large directories add a `LIKE`-based, paginated search endpoint.
- **`PUT /settlement/update` rebuilds the invoice list** from the request (new ids, file metadata dropped). The app only allows editing a settlement header while it has no invoices.
- **Dates are zone-less** (`LocalDate`/`LocalDateTime`); visit responses return `visitDate` as a date-time although requests send a date.
- **Invoice upload** uses plain multipart form fields + a `file` part; **remove invoice** is a `DELETE` with a JSON body.
- **No refresh token:** when the JWT expires the gateway answers 401, the session is cleared and the login screen appears.
- **No "my items" filter:** lists return all visits/settlements; the login response has no link to the rep's MSR id, so the rep is chosen in the forms.

## Next steps

- Encrypt the stored token (Tink + Android Keystore).
- Add `medicalSalesRepId` filters to the list endpoints and map the logged-in user to their MSR.
- Offline cache (Room) for visits recorded without connectivity.
