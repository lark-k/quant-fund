# QuantFund Auth API

Base path: `/api/auth`

All responses use `ApiResponse<T>`:

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": "2026-06-22T12:00:00"
}
```

## Public Endpoints

### POST `/api/auth/register`

Registers a new user and logs the user in immediately.

Request:

```json
{
  "username": "quant_user",
  "password": "QuantFund2026",
  "nickname": "Quant User",
  "phone": "",
  "email": "quant@example.com"
}
```

Response data:

```json
{
  "tokenName": "Authorization",
  "tokenValue": "token",
  "tokenTimeout": 2592000,
  "user": {
    "id": 1,
    "username": "quant_user",
    "nickname": "Quant User",
    "role": "USER",
    "status": "ENABLED",
    "riskLevel": "MEDIUM"
  }
}
```

### POST `/api/auth/login`

Logs in with username and password. Login failure intentionally returns a generic error and does not reveal whether the username exists.

Request:

```json
{
  "username": "quant_user",
  "password": "QuantFund2026"
}
```

### POST `/api/auth/reset-password`

Reserved endpoint for a later password reset workflow. The current implementation returns a business error explaining that the capability is reserved.

## Authenticated Endpoints

Requests to authenticated endpoints must carry the Sa-Token token in the `Authorization` header.

### POST `/api/auth/logout`

Logs out the current token immediately.

### GET `/api/auth/me`

Returns the current logged-in user.

### PUT `/api/auth/profile`

Updates nickname, phone, email, and avatar.

### PUT `/api/auth/password`

Updates the current password after verifying the old password. After a successful password change, existing login sessions for the user are logged out.

## Security Notes

- Passwords are stored as BCrypt hashes only.
- Business endpoints under `/api/**` are intercepted by Sa-Token by default.
- Public exceptions are limited to login, register, reset-password, health, and API docs.
- Business services must use `UserContext.getUserId()` for user-owned data rather than trusting a frontend `user_id`.

