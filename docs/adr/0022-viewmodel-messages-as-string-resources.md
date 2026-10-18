# 0022. ViewModel messages as string resources

Status: Accepted

Date: 2026-10-18

## Context

I18N-1 requires every user-facing string to live in string resources (EN default
+ DE overlay), including messages that ViewModels produce (error snackbars,
status messages, disabled-reason texts). ViewModels previously carried
prebuilt English strings (`ShowError("No household found")`,
`statusMessage: String?`). ViewModels cannot call `stringResource` (no
composition context), so the resource must be resolved at the UI layer.

Alternatives:

1. ViewModels resolve strings via an injected `Application` context and emit
   finished strings. Keeps event types trivial, but bakes the *process* locale
   into the message at emit time (stale after a locale change while the screen
   is alive) and puts presentation concerns in the ViewModel.
2. Events/state carry `@StringRes` resource IDs plus pre-rendered string args,
   resolved in composition (`stringResource`) or via `context.getString` inside
   coroutines/Toasts. Slightly richer event types.

## Decision

Option 2. A shared `ui/UiMessage` data class (`@StringRes resId: Int`,
`args: List<String>`) travels in UI events and state; screens resolve it with
`UiMessage.text()` (composition) or `UiMessage.resolve(context)` (coroutines,
Toasts). Format args are pre-rendered strings — user content or error details,
which are never translated. ViewModel-authored English literals in user-facing
messages are a bug; the exception is debug-only developer-facing text.

## Consequences

- Messages follow the device locale at render time, including after in-place
  locale changes.
- Every new message needs keys in both `values/` and `values-de/` — untested
  by the compiler beyond the EN default; a missing DE string silently falls
  back to English.
- Error-detail args (`error.message`) stay untranslated inside a localized
  template — accepted, they are developer/exception text.
- Dynamic texts that would need runtime selection between many resources
  (e.g. unit preset labels) use resolver functions that map a stored value to
  a resource ID, falling back to the raw value for user-entered content.
