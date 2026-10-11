// Weapons per category, as the ByMykel catalog groups them (`category.name`),
// with snipers split out of its "Rifles". The catalog doesn't store a category
// on `Skin`, so `GET /skins?category=` filters on these `weapon` values (indexed).
// A weapon added to the game lands in no category until it is listed here.
export const WEAPON_CATEGORIES = {
  rifles: ['AK-47', 'AUG', 'FAMAS', 'Galil AR', 'M4A1-S', 'M4A4', 'SG 553'],
  snipers: ['AWP', 'G3SG1', 'SCAR-20', 'SSG 08'],
  pistols: [
    'CZ75-Auto', 'Desert Eagle', 'Dual Berettas', 'Five-SeveN', 'Glock-18',
    'P2000', 'P250', 'R8 Revolver', 'Tec-9', 'USP-S',
  ],
  smgs: ['MAC-10', 'MP5-SD', 'MP7', 'MP9', 'P90', 'PP-Bizon', 'UMP-45'],
  heavy: ['M249', 'MAG-7', 'Negev', 'Nova', 'Sawed-Off', 'XM1014'],
  knives: [
    'Bayonet', 'Bowie Knife', 'Butterfly Knife', 'Classic Knife', 'Falchion Knife',
    'Flip Knife', 'Gut Knife', 'Huntsman Knife', 'Karambit', 'Kukri Knife', 'M9 Bayonet',
    'Navaja Knife', 'Nomad Knife', 'Paracord Knife', 'Shadow Daggers', 'Skeleton Knife',
    'Stiletto Knife', 'Survival Knife', 'Talon Knife', 'Ursus Knife',
  ],
  gloves: [
    'Bloodhound Gloves', 'Broken Fang Gloves', 'Driver Gloves', 'Hand Wraps',
    'Hydra Gloves', 'Moto Gloves', 'Specialist Gloves', 'Sport Gloves',
  ],
} as const

export type WeaponCategory = keyof typeof WEAPON_CATEGORIES

export const WEAPON_CATEGORY_KEYS = Object.keys(WEAPON_CATEGORIES) as [WeaponCategory, ...WeaponCategory[]]
