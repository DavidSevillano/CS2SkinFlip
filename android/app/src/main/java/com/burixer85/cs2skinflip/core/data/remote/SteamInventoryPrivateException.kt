package com.burixer85.cs2skinflip.core.data.remote

/** Thrown when the device's direct call to Steam's inventory endpoint returns 401/403 —
 *  i.e. the user's Steam inventory privacy is set to private or friends-only. */
class SteamInventoryPrivateException : Exception("Steam inventory is private")
