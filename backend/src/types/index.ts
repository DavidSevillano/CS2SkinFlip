// JWT payload type — augments @fastify/jwt so request.user is typed everywhere
declare module '@fastify/jwt' {
  interface FastifyJWT {
    payload: JwtPayload
    user: JwtPayload
  }
}

export interface JwtPayload {
  userId: string
  // Optional — null for users who registered via email/password without linking Steam
  steamId: string | null
  // Only set on single-use tokens (password reset / email verification links).
  // Session tokens never have this — authenticate() rejects any token that does.
  purpose?: 'password_reset' | 'verify_email'
}

export interface SteamPlayer {
  steamid: string
  personaname: string
  avatarfull: string
  profileurl: string
}

export interface InventoryAsset {
  assetid: string
  classid: string
  instanceid: string
  amount: string
}

export interface InventoryDescription {
  classid: string
  instanceid: string
  market_hash_name: string
  name: string
  icon_url: string
  tags: Array<{
    category: string
    internal_name: string
    localized_tag_name: string
  }>
}

export interface InventoryItem {
  assetId: string
  marketHashName: string
  name: string
  iconUrl: string
  amount: number
  tags: InventoryDescription['tags']
}

export interface AggregatedPrices {
  skinId: string
  marketHashName: string
  skinportPrice: number | null
  csgoMarketPrice: number | null
  waxpeerPrice: number | null
  lowestPrice: number | null
  priceChange24h: number | null
  volume24h: number | null
  updatedAt: string
}
