package com.burixer85.cs2skinflip.core.data.remote

/** Thrown when the device's direct call to Steam's inventory endpoint returns 401/403 —
 *  i.e. the user's Steam inventory privacy is set to private or friends-only. */
class SteamInventoryPrivateException : Exception("Steam inventory is private")

/** Thrown when Steam answers the inventory call with 429 — retrying in a few minutes works. */
class SteamRateLimitedException : Exception("Steam is rate-limiting inventory requests")
