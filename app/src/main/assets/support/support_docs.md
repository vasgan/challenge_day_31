# Known Issues

## AUTH-101
**Title:** invalid session token after password reset on Android  
**Status:** known  
**Affected area:** authentication  
**Affected versions:** Android 2.8.1  
**Description:** После смены пароля старый refresh token может оставаться в local secure storage, из-за чего приложение продолжает использовать невалидную сессию.

**Workaround:** logout + force close + login again  
**Recommended investigation:** проверить очистку secure storage во время password reset recovery flow

---

## AUTH-104
**Title:** logout does not fully clear session on Android  
**Status:** investigating  
**Affected area:** authentication  
**Affected versions:** Android 2.8.1  
**Description:** Нажатие logout очищает UI state, но в некоторых случаях не удаляет refresh token.

**Workaround:** reinstall app or clear app data  
**Recommended investigation:** проверить logout handler и token storage cleanup

---

## AUTH-108
**Title:** email verification code delayed  
**Status:** intermittent  
**Affected area:** authentication  
**Affected versions:** all  
**Description:** В отдельные периоды письма с verification code приходят с задержкой до 15–20 минут.

**Workaround:** retry later, check spam  
**Recommended investigation:** проверить mail provider latency

---

## DASH-021
**Title:** infinite dashboard loading in Safari  
**Status:** known  
**Affected area:** dashboard  
**Affected versions:** web  
**Description:** После логина стартовый bootstrap запрос может завершиться ошибкой в Safari, оставляя UI в loading state.

**Workaround:** retry in Chrome  
**Recommended investigation:** проверить browser-specific bootstrap flow

---

## BILL-007
**Title:** blank payment screen on iOS  
**Status:** known  
**Affected area:** billing  
**Affected versions:** iOS 3.1.0  
**Description:** WebView не всегда корректно обрабатывает payment redirect.

**Workaround:** открыть оплату в системном браузере  
**Recommended investigation:** проверить deep link / payment redirect handling

---

## API-013
**Title:** 403 for new API tokens without required scopes  
**Status:** known  
**Affected area:** api  
**Affected versions:** all  
**Description:** Пользователи создают новый token, но не добавляют нужные scopes, после чего получают 403 Forbidden.

**Workaround:** regenerate token with required permissions  
**Recommended investigation:** улучшить UX при создании API token

---

## WS-003
**Title:** invite failed in restricted workspace  
**Status:** investigating  
**Affected area:** workspace  
**Affected versions:** web  
**Description:** В некоторых workspace приглашение падает из-за конфликта permissions policy.

**Workaround:** проверить роль администратора  
**Recommended investigation:** проверить workspace-specific policy merge