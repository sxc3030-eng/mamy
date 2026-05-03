# Google Cloud OAuth setup for MamY

1. Go to https://console.cloud.google.com/, create project `mamy-android-prod` (and `mamy-android-dev`).
2. Enable APIs : `Google Calendar API`.
3. OAuth consent screen :
   - User type : External
   - App name : `MamY`
   - Support email : <ops>
   - Scopes : `https://www.googleapis.com/auth/calendar.readonly`
   - Test users (during dev) : add your dogfood Google accounts.
4. Credentials -> Create Credentials -> OAuth Client ID :
   - Type : Web application (used for `setServerClientId` server token mint)
   - Name : `mamy-web-token-mint`
   - Copy the Client ID into `app/src/main/res/values/calendar_config.xml` `google_oauth_web_client_id`.
5. Credentials -> Create Credentials -> OAuth Client ID :
   - Type : Android
   - Package name : `com.mamy.android`
   - SHA-1 : output of `keytool -keystore <ks> -list -v` (debug + prod separate entries)
6. Update `calendar_config.xml` after each environment switch.
