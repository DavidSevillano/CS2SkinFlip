// JWT payload type — augments @fastify/jwt so request.user is typed everywhere
declare module '@fastify/jwt' {
  interface FastifyJWT {
    payload: JwtPayload
    user: JwtPayload
  }
}

export interface JwtPayload {
  userId: string
  steamId: string | null
}

export interface SteamPlayer {
  steamid: string
  personaname: string
  avatarfull: string
  profileurl: string
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
