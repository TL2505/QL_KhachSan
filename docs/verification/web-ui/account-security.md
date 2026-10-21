# Shared account settings and email OTP

Admin, staff and customer profile routes use `js/shared/account-settings.js` and the locally built daisyUI stylesheet. Customer profile displays only editable personal information and retains the existing stay information and identity scanner in a separate disclosure. Customer security displays email verification with a read-only saved email and the password/OTP flow, without repeating the personal form. Admin and staff retain the combined layout. Both source and public JavaScript copies are synchronized.

Customer surfaces, fields, sidebar and actions share the membership palette. The theme bridge has the same specificity as the old dark/light rules so an obsolete fixed panel background cannot override the tier. Dashboard and other customer routes prefer lifetime points from the authenticated overview endpoint, with legacy history fallback. Redemptions and incomplete history do not change the tier when stored totals are available. Late navigation responses cannot overwrite the current view or its membership palette.

The authenticated `/api/v1/account/profile` endpoint selects the user from the verified JWT, never a supplied username or customer ID. Updating a linked customer profile changes users and customers in one transaction. Email verification is bound to the stored address through `users.verified_email`; changing an address through an existing profile endpoint also invalidates verification. Startup adds this nullable column to an existing schema.

`POST /account/email/request` sends a six digit code to the saved email; `/account/email/verify` confirms it. Password changes first require a verified email and the current password at `/account/password/request`, then a purpose-specific OTP plus the new password at `/account/password/confirm`. Legacy password routes also require OTP. Successful confirmation revokes the current bearer token and the UI requests a fresh login.

Codes expire five minutes after mail delivery, allow five incorrect attempts before a ten minute lock, and support a sixty second resend interval with a ten message per hour limit per user and purpose. Codes are salted and hashed in process memory, used once, bound to user, purpose and address, and never returned by the API. Password challenges are additionally bound to the current password hash. Incorrect codes return 422 so they do not clear the login session. SMTP uses the existing SMTP environment configuration; errors remain recoverable without exposing server internals. OTP POST requests are never automatically replayed to another backend.

Challenges and rate limits are process-local, so a restart requires a new code. A deployment with multiple backend instances should move this state and token revocation into a shared store before scaling out. Verification tests use an injected mailer and disposable database; they do not send real email or change real account passwords.

Validation completed:

- 119 web tests passed, including shared role rendering, verification, password confirmation, incorrect OTP recovery, separate customer tabs, profile changes and navigation during a pending request.
- 12 account security Java tests passed, including HTTP authorization for all three roles, legacy bypass prevention, token revocation, lifecycle limits and an isolated MySQL transaction test. Existing Google OTP lifecycle and Vietnamese mail encoding tests also passed.
- Browser checks passed for all four membership tiers across dashboard, profile and security in both themes (24 combinations). Six compact Gold layouts also passed without horizontal overflow. Existing role checks covered admin/staff and the shared OTP component; customer stay information and identity controls remain available under Profile.

Commands:

```powershell
npm test --prefix web-ui
npm run build:account-settings --prefix web-ui
mvn -q '-Dtest=AccountSecurityTest,AccountSecurityControllerTest,GoogleOtpLifecycleTest,GoogleOtpMailEncodingTest' test
$env:HOTEL_RUN_ACCOUNT_DB_TESTS='true'
mvn -q '-Dtest=AccountSecurityDatabaseTest' test
```

The opt-in database test creates and drops only its own randomly named test schema on local MySQL, using the local disposable development server credentials. Browser screenshots use synthetic account details and a mock mailer. The temporary browser fixture was removed after verification.
