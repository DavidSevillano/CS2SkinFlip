// Generates Play Store screenshot HTML files (540x960 CSS px, captured at 2x = 1080x1920)
// plus a 1024x500 feature graphic. Run: node build.mjs  (then capture.mjs)
import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT = join(__dirname, 'html')
mkdirSync(OUT, { recursive: true })

// ---- Real skin catalog images (ByMykel/CSGO-API, same source as the backend) ----
const IMG = {
  redline: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyLwlcK3wiFO0POlPPNSI_-RHGavzedxuPUnFniykEtzsWWBzoyuIiifaAchDZUjTOZe4RC_w4buM-6z7wzbgokUyzK-0H08hRGDMA',
  awpAsiimov: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyLwiYbf_jdk7uW-V6V-Kf2cGFidxOp_pewnF3nhxEt0sGnSzN76dH3GOg9xC8FyEORftRe-x9PuYurq71bW3d8UnjK-0H0YSTpMGQ',
  deagleBlaze: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyL1m5fn8Sdk7vORbqhsLfWAMWuZxuZi_uI_TX6wxxkjsGXXnImsJ37COlUoWcByEOMOtxa5kdXmNu3htVPZjN1bjXKpkHLRfQU',
  glockFade: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyL2kpnj9h1a7s2oaaBoH_yaCW-Ej-8u5bZvHnq1w0Vz62TUzNj4eCiVblMmXMAkROJeskLpkdXjMrzksVTAy9US8PY25So',
  printstream: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyL8ypexwjFS4_ega6F_H_OGMWrEwL9lj_F7Rienhgk1tjyIpYPwJiPTcAAoCpsiEO5ZsUbpm9C2Zuni4VHW3o5EzSX62HxP7Sg96-hWVqYi_6TJz1aW0nxrkGs',
  butterflyDoppler: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyL6kJ_m-B1Z-ua6bbZrLOmsD2qvw-J3s-p5SiihmSIqsi-HlorwOy7DAVRPVssnHaMUuhe9xIHlMuvqtgPf2IoTyC383Sod7CY-sr4DVfZ2qKPU3g-TNuE-545DeqjFvb87vg',
  killConfirmed: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyLkjYbf7itX6vytbbZSI-WsG3SA_uV_vO1WTCa9kxQ1vjiBpYPwJiPTcFB2Xpp5TO5cskG9lYCxZu_jsVCL3o4Xnij23ClO5ik9tegFA_It8qHJz1aWe-uc160',
  vulcan: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyLwlcK3wiFO0POlPPNSMuWRDGKC_uJ_t-l9AXCxxEh14zjTztivci2ePQZ2W8NzTecD4BKwloLiYeqxtAOIj9gUyyngznQeF7I6QE8',
  m4Neonoir: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyL8ypexwiFO0P_6afBSLvWcMWmfyPxJvOhuRz39wE1142vSztmvInvBOgV0W5R1FLYNuxW4wIbgNrmx4g2Kj4tMmCX93zQJsHgJr0dqFw',
  lightningStrike: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyLwiYbf_C9k4_upYLBjKf6UMWaH0dF6ueZhW2frwU1_sW2EmNyvc32RZwMpCpcjQ-EJ4xbtmt3gYezk4wzb3tpAy3mrkGoXubsGIfVN',
  howl: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyL8ypexwiFO0P_6afVSKP-EAm6extF6ueZhW2exwkl2tmTXwt39eCiUPQR2DMN4TOVetUK8xoLgM-K341eM2otDnC6okGoXufBz_TAB',
  akAsiimov: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyLwlcK3wiFO0POlPPNSIeOaB2qf19F6ueZhW2e2wEt-t2jcytf6dymSO1JxA5oiRecLsRa5kIfkYr-241aLgotHz3-rkGoXuUp8oX57',
  karambitFade: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyL6kJ_m-B1Q7uCvZaZkNM-SD1iWwOpzj-1gSCGn20tztm_UyIn_JHKUbgYlWMcmQ-ZcskSwldS0MOnntAfd3YlMzH35jntXrnE8SOGRGG8',
  awpNeonoir: 'https://community.akamai.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyLwiYbf_jdk7uW-V6poL_6cB3WvzedxuPUnHirrxR4l423SyI39I3KXPwdxWZclQeNZ5EXskYfnNeyw71OMi9lNzDK-0H3r66pOTw',
}

// ---- Theme (Color.kt) ----
const C = {
  bg: '#0D1117', surface: '#161B22', surfaceVariant: '#1C2128', elevated: '#21262D',
  orange: '#FF6B35', orangeVar: '#FF8C61', blue: '#58A6FF', green: '#3FB950', red: '#F85149',
  text: '#E6EDF3', text2: '#8B949E', text3: '#484F58', divider: '#30363D',
  classified: '#D32CE6', covert: '#EB4B4B', restricted: '#8847FF', contraband: '#E4AE39', knife: '#E4AE39',
}

// ---- SVG icons (Material) ----
const icon = (d, size = 24, color = 'currentColor') =>
  `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="${color}"><path d="${d}"/></svg>`
const D = {
  home: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z',
  search: 'M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z',
  bell: 'M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z',
  gear: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  trendUp: 'M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z',
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  star: 'M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z',
  open: 'M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z',
  sync: 'M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46A7.93 7.93 0 0 0 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74A7.93 7.93 0 0 0 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z',
  plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  filter: 'M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z',
}

// ---- Shared shell ----
const shell = (title, caption, phoneContent, opts = {}) => `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>${title}</title><style>
* { margin:0; padding:0; box-sizing:border-box; }
html,body { width:540px; height:960px; overflow:hidden; }
body {
  font-family:'Segoe UI', Roboto, -apple-system, sans-serif;
  background:
    radial-gradient(ellipse 480px 360px at 50% 42%, rgba(255,107,53,.16), transparent 70%),
    radial-gradient(ellipse 600px 500px at 50% 110%, rgba(88,166,255,.07), transparent 70%),
    linear-gradient(180deg, #10151d 0%, #0D1117 100%);
  color:${C.text};
  display:flex; flex-direction:column; align-items:center;
}
.caption { text-align:center; padding:44px 40px 26px; }
.eyebrow { font-size:15px; font-weight:700; letter-spacing:3px; color:${C.orange}; text-transform:uppercase; margin-bottom:10px; }
.headline { font-size:34px; font-weight:800; line-height:1.18; letter-spacing:-.3px; }
.headline .hl { color:${C.orange}; }
.phone {
  width:406px; flex:1; min-height:0;
  border:10px solid #2a3038; border-bottom:none; border-radius:44px 44px 0 0;
  background:${C.bg}; overflow:hidden; position:relative;
  box-shadow: 0 -2px 0 2px #11151b, 0 30px 80px rgba(0,0,0,.6), 0 0 70px rgba(255,107,53,.12);
}
.screen { width:386px; height:100%; background:${C.bg}; display:flex; flex-direction:column; position:relative; }
.statusbar { height:32px; display:flex; align-items:center; justify-content:space-between; padding:0 20px; font-size:12.5px; font-weight:600; color:${C.text}; background:${opts.statusbarBg ?? C.surface}; }
.statusbar .sbi { display:flex; gap:5px; align-items:center; }
.content { flex:1; min-height:0; overflow:hidden; display:flex; flex-direction:column; }
.bottomnav { position:absolute; left:0; right:0; bottom:0; height:68px; background:${C.surface}; border-top:1px solid ${C.divider}; display:flex; }
.navitem { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:3px; font-size:10.5px; color:${C.text2}; }
.navitem.active { color:${C.orange}; }
.navitem.active .pill { background:rgba(255,107,53,.18); border-radius:14px; padding:2px 14px; }
.navitem .pill { padding:2px 14px; display:flex; }
.chip-up { background:rgba(63,185,80,.15); color:${C.green}; font-size:11px; font-weight:700; padding:2px 7px; border-radius:6px; }
.chip-down { background:rgba(248,81,73,.15); color:${C.red}; font-size:11px; font-weight:700; padding:2px 7px; border-radius:6px; }
.imgbox { background:${C.elevated}; border-radius:6px; display:flex; align-items:center; justify-content:center; overflow:hidden; flex:none; }
.imgbox img { width:88%; height:88%; object-fit:contain; }
${opts.css ?? ''}
</style></head><body>
<div class="caption">${caption}</div>
<div class="phone"><div class="screen">
  <div class="statusbar"><span>12:30</span><span class="sbi">
    ${icon('M12 21l8.5-8.5c-.83-.62-3.94-3-8.5-3s-7.67 2.38-8.5 3L12 21zm0-16c5.52 0 10 2.24 11.5 3.5L12 21 .5 8.5C2 7.24 6.48 5 12 5z'.replace(/M12 21l8.5.*?12 21zm/, 'm'), 14)}
    <svg viewBox="0 0 24 24" width="14" height="14" fill="${C.text}"><path d="M12.01 21.49L23.64 7c-.45-.34-4.93-4-11.64-4C5.28 3 .81 6.66.36 7l11.63 14.49.01.01.01-.01z"/></svg>
    <svg viewBox="0 0 24 24" width="14" height="14" fill="${C.text}"><path d="M15.67 4H14V2h-4v2H8.33C7.6 4 7 4.6 7 5.33v15.33C7 21.4 7.6 22 8.33 22h7.33c.74 0 1.34-.6 1.34-1.33V5.33C17 4.6 16.4 4 15.67 4z"/></svg>
  </span></div>
  <div class="content">${phoneContent}</div>
</div></div>
</body></html>`

const bottomNav = (active) => `<div class="bottomnav">
  ${[['Home', D.home], ['Search', D.search], ['Alerts', D.bell], ['Settings', D.gear]].map(([label, d]) =>
    `<div class="navitem${label === active ? ' active' : ''}"><span class="pill">${icon(d, 22)}</span><span>${label}</span></div>`).join('')}
</div>`

const appHeader = `<div style="background:${C.surface};padding:14px 20px;">
  <div style="font-size:21px;font-weight:800;color:${C.orange};">CS2 SkinFlip</div>
  <div style="font-size:12px;color:${C.text2};">Live market intelligence</div>
</div>`

// =============== 1. HOME / TOP MOVERS ===============
const moverRow = (rank, img, name, wear, price, change, up = true) => `
<div style="display:flex;align-items:center;padding:9px 16px;gap:10px;">
  <span style="width:20px;font-size:13px;color:${C.text2};">${rank}</span>
  <div class="imgbox" style="width:46px;height:46px;"><img src="${img}"></div>
  <div style="flex:1;min-width:0;">
    <div style="font-size:13px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${name}</div>
    <div style="font-size:11px;color:${C.text2};">${wear}</div>
  </div>
  <div style="text-align:right;">
    <div style="font-size:13px;font-weight:700;">${price}</div>
    <div style="margin-top:2px;"><span class="${up ? 'chip-up' : 'chip-down'}">${change}</span></div>
  </div>
</div>
<div style="height:1px;background:${C.divider};margin:0 16px;"></div>`

const homeScreen = shell('home', `
  <div class="eyebrow">Market Trends</div>
  <div class="headline">Spot the market's<br><span class="hl">biggest movers</span></div>`, `
${appHeader}
<div style="display:flex;background:${C.surface};border-bottom:1px solid ${C.divider};">
  <div style="flex:1;text-align:center;padding:12px 0 10px;color:${C.orange};font-size:14px;font-weight:600;border-bottom:2.5px solid ${C.orange};">Rising</div>
  <div style="flex:1;text-align:center;padding:12px 0 10px;color:${C.text2};font-size:14px;font-weight:500;">Falling</div>
</div>
<div style="display:flex;align-items:center;justify-content:space-between;padding:12px 16px;">
  <div style="display:flex;align-items:center;gap:8px;">
    <span style="width:28px;height:28px;border-radius:6px;background:rgba(255,107,53,.15);display:flex;align-items:center;justify-content:center;">${icon(D.trendUp, 16, C.orange)}</span>
    <span style="font-size:15px;font-weight:600;">Top Movers · 24h</span>
  </div>
  <span style="font-size:10.5px;color:${C.text2};">Vol by movement</span>
</div>
${moverRow(1, IMG.akAsiimov, 'AK-47 | Asiimov', 'FT', '$182.40', '+12.4%')}
${moverRow(2, IMG.butterflyDoppler, '★ Butterfly Knife | Doppler', 'FN', '$1,942.00', '+8.7%')}
${moverRow(3, IMG.awpAsiimov, 'AWP | Asiimov', 'FT', '$154.16', '+7.2%')}
${moverRow(4, IMG.printstream, 'M4A1-S | Printstream', 'MW', '$412.85', '+6.8%')}
${moverRow(5, IMG.deagleBlaze, 'Desert Eagle | Blaze', 'FN', '$706.30', '+5.9%')}
${moverRow(6, IMG.redline, 'AK-47 | Redline', 'FT', '$44.52', '+5.1%')}
${moverRow(7, IMG.glockFade, 'Glock-18 | Fade', 'FN', '$1,318.00', '+4.6%')}
${moverRow(8, IMG.killConfirmed, 'USP-S | Kill Confirmed', 'MW', '$118.72', '+3.9%')}
${bottomNav('Home')}`)

// =============== 2. SKIN DETAIL / MARKETPLACE COMPARISON ===============
const marketRow = (name, price, lowest = false) => `
<div style="display:flex;align-items:center;justify-content:space-between;background:${C.surface};border:1px solid ${lowest ? C.orange : C.divider};border-radius:10px;padding:13px 14px;margin-bottom:9px;">
  <div style="display:flex;align-items:center;gap:9px;">
    <span style="font-size:14px;font-weight:600;">${name}</span>
    ${lowest ? `<span style="background:${C.orange};color:#fff;font-size:9.5px;font-weight:800;letter-spacing:.5px;padding:2.5px 7px;border-radius:5px;">LOWEST</span>` : ''}
  </div>
  <div style="display:flex;align-items:center;gap:8px;">
    <span style="font-size:15px;font-weight:700;color:${lowest ? C.orange : C.text};">${price}</span>
    ${icon(D.open, 15, C.text2)}
  </div>
</div>`

const detailScreen = shell('detail', `
  <div class="eyebrow">Compare &amp; Save</div>
  <div class="headline">The <span class="hl">lowest price</span><br>across marketplaces</div>`, `
<div style="padding:12px 16px;display:flex;justify-content:space-between;align-items:center;">
  ${icon(D.back, 22, C.text)}
  ${icon(D.star, 22, C.orange)}
</div>
<div style="display:flex;justify-content:center;padding:2px 0 10px;">
  <div style="width:200px;height:150px;display:flex;align-items:center;justify-content:center;filter:drop-shadow(0 12px 24px rgba(235,75,75,.25));">
    <img src="${IMG.awpAsiimov}" style="width:100%;object-fit:contain;">
  </div>
</div>
<div style="text-align:center;padding:0 20px;">
  <div style="font-size:21px;font-weight:800;">Asiimov</div>
  <div style="font-size:13px;color:${C.text2};margin-top:1px;">AWP</div>
  <div style="display:flex;gap:6px;justify-content:center;margin-top:9px;">
    <span style="border:1px solid ${C.divider};color:${C.text2};font-size:11px;padding:3px 10px;border-radius:12px;">Field-Tested</span>
    <span style="border:1px solid ${C.covert};color:${C.covert};font-size:11px;padding:3px 10px;border-radius:12px;">Covert</span>
  </div>
</div>
<div style="padding:18px 16px 0;">
  <div style="font-size:15px;font-weight:700;margin-bottom:10px;">Marketplace Prices</div>
  ${marketRow('Waxpeer', '$154.16', true)}
  ${marketRow('Skinport', '$158.90')}
  ${marketRow('CS:GO Market', '$161.34')}
</div>
<div style="padding:6px 16px;">
  <div style="font-size:15px;font-weight:700;margin-bottom:8px;">Float Range</div>
  <div style="display:flex;background:${C.surface};border:1px solid ${C.divider};border-radius:10px;padding:12px 8px;">
    ${[['Min', '0.1800'], ['Median', '0.2650'], ['Max', '0.3500']].map(([l, v]) =>
      `<div style="flex:1;text-align:center;"><div style="font-size:13px;font-weight:600;">${v}</div><div style="font-size:11px;color:${C.text2};">${l}</div></div>`).join('')}
  </div>
</div>
${bottomNav('Search')}`)

// =============== 3. PRICE ALERTS ===============
const alertCard = (img, name, sub, target, current, on = true) => `
<div style="background:${C.surface};border:1px solid ${C.divider};border-radius:12px;padding:13px 14px;margin:0 16px 10px;display:flex;gap:11px;align-items:center;">
  <div class="imgbox" style="width:52px;height:52px;border-radius:8px;"><img src="${img}"></div>
  <div style="flex:1;min-width:0;">
    <div style="font-size:13.5px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${name}</div>
    <div style="font-size:11.5px;color:${C.orange};font-weight:600;margin-top:2px;">${sub} ${target}</div>
    <div style="font-size:11px;color:${C.text2};margin-top:2px;">Current market price: ${current}</div>
  </div>
  <div style="width:40px;height:22px;border-radius:12px;flex:none;position:relative;background:${on ? C.orange : C.text3};">
    <div style="position:absolute;top:2.5px;${on ? 'right:3px' : 'left:3px'};width:17px;height:17px;border-radius:50%;background:#fff;"></div>
  </div>
</div>`

const alertsScreen = shell('alerts', `
  <div class="eyebrow">Price Alerts</div>
  <div class="headline">Get notified the<br>moment <span class="hl">prices drop</span></div>`, `
<div style="background:${C.surface};padding:14px 20px;display:flex;justify-content:space-between;align-items:center;">
  <div style="font-size:19px;font-weight:800;">Price Alerts</div>
  <div style="width:34px;height:34px;border-radius:10px;background:${C.orange};display:flex;align-items:center;justify-content:center;">${icon(D.plus, 20, '#fff')}</div>
</div>
<div style="margin:12px 14px 14px;background:${C.elevated};border:1px solid ${C.divider};border-radius:14px;padding:12px 14px;box-shadow:0 8px 24px rgba(0,0,0,.45);">
  <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px;">
    <div style="width:20px;height:20px;border-radius:5px;background:${C.orange};display:flex;align-items:center;justify-content:center;">${icon(D.bell, 13, '#fff')}</div>
    <span style="font-size:11.5px;color:${C.text2};">CS2 SkinFlip · now</span>
  </div>
  <div style="font-size:13.5px;font-weight:700;">🔻 Price alert: AK-47 | Redline (FT)</div>
  <div style="font-size:12.5px;color:${C.text2};margin-top:2px;">Dropped to $42.10 — below your $45.00 target</div>
</div>
${alertCard(IMG.redline, 'AK-47 | Redline (FT)', 'Below', '$45.00', '$44.52')}
${alertCard(IMG.butterflyDoppler, '★ Butterfly Knife | Doppler (FN)', 'Below', '$1,800.00', '$1,942.00')}
${alertCard(IMG.vulcan, 'AK-47 | Vulcan (MW)', 'Below', '$900.00', '$948.20')}
${alertCard(IMG.awpNeonoir, 'AWP | Neo-Noir (FN)', 'Above', '$120.00', '$112.35', false)}
${bottomNav('Alerts')}`)

// =============== 4. PRICE HISTORY ===============
// smooth-ish rising series for the chart
const pts = [62, 61.2, 61.8, 60.5, 59.8, 60.4, 58.9, 58.2, 58.8, 57.5, 56.2, 56.9, 55.1, 54.3, 55, 53.2, 52.1, 52.8, 50.9, 49.5, 50.2, 48.1, 46.8, 47.6, 45.9, 44.2, 45.1, 43.3, 42.5, 43.1]
const W = 330, H = 150, minV = 40, maxV = 65
const xy = pts.map((v, i) => [10 + i * ((W - 20) / (pts.length - 1)), H - ((v - minV) / (maxV - minV)) * (H - 20) - 5])
const path = xy.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ')
const chartSvg = `
<svg viewBox="0 0 ${W} ${H}" width="100%" style="display:block;">
  <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="${C.orange}" stop-opacity=".35"/><stop offset="1" stop-color="${C.orange}" stop-opacity="0"/>
  </linearGradient></defs>
  ${[0.25, 0.5, 0.75].map(f => `<line x1="10" x2="${W - 10}" y1="${H * f}" y2="${H * f}" stroke="${C.divider}" stroke-width="1" stroke-dasharray="3 4"/>`).join('')}
  <path d="${path} L${xy[xy.length - 1][0]},${H} L${xy[0][0]},${H} Z" fill="url(#g)"/>
  <path d="${path}" fill="none" stroke="${C.orange}" stroke-width="2.5" stroke-linecap="round"/>
  <circle cx="${xy[xy.length - 1][0]}" cy="${xy[xy.length - 1][1]}" r="4.5" fill="${C.orange}" stroke="#fff" stroke-width="1.5"/>
</svg>`

const historyScreen = shell('history', `
  <div class="eyebrow">Price History</div>
  <div class="headline">Buy the dip,<br><span class="hl">sell the spike</span></div>`, `
<div style="padding:12px 16px;display:flex;justify-content:space-between;align-items:center;">
  ${icon(D.back, 22, C.text)}
  ${icon(D.star, 22, C.orange)}
</div>
<div style="display:flex;justify-content:center;padding:0 0 8px;">
  <div style="width:190px;height:140px;display:flex;align-items:center;justify-content:center;filter:drop-shadow(0 12px 24px rgba(211,44,230,.22));">
    <img src="${IMG.redline}" style="width:100%;object-fit:contain;">
  </div>
</div>
<div style="text-align:center;">
  <div style="font-size:21px;font-weight:800;">Redline</div>
  <div style="font-size:13px;color:${C.text2};">AK-47</div>
  <div style="margin-top:8px;"><span style="font-size:26px;font-weight:800;color:${C.orange};">$44.52</span>
  <span class="chip-down" style="font-size:12.5px;margin-left:6px;">−7.4% · 30d</span></div>
</div>
<div style="margin:16px 16px 0;background:${C.surface};border:1px solid ${C.divider};border-radius:14px;padding:14px;">
  <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
    <span style="font-size:15px;font-weight:700;">Price History</span>
    <div style="display:flex;gap:5px;">
      ${['24H', '7D', '1M', '3M'].map((r, i) => `<span style="font-size:11px;font-weight:600;padding:4px 10px;border-radius:8px;${i === 2 ? `background:${C.orange};color:#fff;` : `color:${C.text2};border:1px solid ${C.divider};`}">${r}</span>`).join('')}
    </div>
  </div>
  ${chartSvg}
  <div style="display:flex;justify-content:space-between;color:${C.text3};font-size:10.5px;margin-top:6px;"><span>Jun 11</span><span>Jun 26</span><span>Jul 11</span></div>
</div>
<div style="margin:12px 16px 0;display:flex;gap:10px;">
  <div style="flex:1;background:${C.surface};border:1px solid ${C.divider};border-radius:12px;padding:11px;text-align:center;">
    <div style="font-size:11px;color:${C.text2};">30d low</div><div style="font-size:15px;font-weight:700;color:${C.green};">$42.10</div>
  </div>
  <div style="flex:1;background:${C.surface};border:1px solid ${C.divider};border-radius:12px;padding:11px;text-align:center;">
    <div style="font-size:11px;color:${C.text2};">30d high</div><div style="font-size:15px;font-weight:700;color:${C.red};">$62.04</div>
  </div>
</div>
${bottomNav('Search')}`)

// =============== 5. PORTFOLIO ===============
const portfolioRow = (img, name, wear, qty, value, change, up) => `
<div style="display:flex;align-items:center;gap:11px;background:${C.surface};border:1px solid ${C.divider};border-radius:12px;padding:11px 13px;margin:0 16px 9px;">
  <div class="imgbox" style="width:50px;height:50px;border-radius:8px;"><img src="${img}"></div>
  <div style="flex:1;min-width:0;">
    <div style="font-size:13px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${name}</div>
    <div style="font-size:11px;color:${C.text2};margin-top:2px;">${wear} · ×${qty}</div>
  </div>
  <div style="text-align:right;">
    <div style="font-size:13.5px;font-weight:700;">${value}</div>
    <div style="margin-top:2px;"><span class="${up ? 'chip-up' : 'chip-down'}">${change}</span></div>
  </div>
</div>`

const portfolioScreen = shell('portfolio', `
  <div class="eyebrow">Portfolio</div>
  <div class="headline">Know what your<br>inventory is <span class="hl">really worth</span></div>`, `
<div style="background:${C.surface};padding:14px 20px;font-size:19px;font-weight:800;">Portfolio</div>
<div style="margin:14px 16px;background:linear-gradient(135deg, rgba(255,107,53,.16), rgba(255,107,53,.04));border:1px solid rgba(255,107,53,.35);border-radius:16px;padding:18px;">
  <div style="font-size:12px;color:${C.text2};">Total inventory value</div>
  <div style="display:flex;align-items:baseline;gap:10px;margin-top:4px;">
    <span style="font-size:32px;font-weight:800;">$3,847.62</span>
    <span class="chip-up" style="font-size:13px;">+3.2% today</span>
  </div>
  <div style="display:flex;gap:8px;margin-top:14px;">
    <div style="flex:1;display:flex;align-items:center;justify-content:center;gap:7px;background:${C.orange};border-radius:10px;padding:9px 0;font-size:12.5px;font-weight:700;color:#fff;">${icon(D.sync, 15, '#fff')} Sync Steam inventory</div>
    <div style="width:40px;display:flex;align-items:center;justify-content:center;background:${C.elevated};border:1px solid ${C.divider};border-radius:10px;">${icon(D.plus, 18, C.text)}</div>
  </div>
</div>
<div style="display:flex;justify-content:space-between;padding:0 18px 9px;font-size:13px;">
  <span style="font-weight:700;">Your items · 14</span><span style="color:${C.text2};">Sorted by value</span>
</div>
${portfolioRow(IMG.karambitFade, '★ Karambit | Fade', 'FN', 1, '$2,412.00', '+4.1%', true)}
${portfolioRow(IMG.printstream, 'M4A1-S | Printstream', 'MW', 1, '$412.85', '+6.8%', true)}
${portfolioRow(IMG.lightningStrike, 'AWP | Lightning Strike', 'FN', 1, '$398.40', '−1.2%', false)}
${portfolioRow(IMG.akAsiimov, 'AK-47 | Asiimov', 'FT', 2, '$364.80', '+12.4%', true)}
${portfolioRow(IMG.killConfirmed, 'USP-S | Kill Confirmed', 'MW', 1, '$118.72', '+3.9%', true)}
${bottomNav('Home')}`)

// =============== 6. SEARCH ===============
const searchCard = (img, name, wear, rarity, rarityColor, price, change, up = true) => `
<div style="background:linear-gradient(90deg, ${rarityColor}14, ${C.surface});border:1px solid ${C.divider};border-radius:12px;padding:11px;margin:0 16px 9px;display:flex;gap:11px;align-items:center;">
  <div class="imgbox" style="width:56px;height:56px;border-radius:8px;"><img src="${img}"></div>
  <div style="flex:1;min-width:0;">
    <div style="font-size:13.5px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${name}</div>
    <div style="display:flex;align-items:center;gap:6px;margin-top:3px;">
      <span style="font-size:11px;color:${C.text2};">${wear}</span>
      <span style="font-size:10px;font-weight:700;color:${rarityColor};border:1px solid ${rarityColor}55;padding:1.5px 7px;border-radius:9px;">${rarity}</span>
    </div>
    <div style="font-size:14.5px;font-weight:700;margin-top:4px;">${price}</div>
  </div>
  <span class="${up ? 'chip-up' : 'chip-down'}">${change}</span>
</div>`

const searchScreen = shell('search', `
  <div class="eyebrow">Skin Search</div>
  <div class="headline">Search <span class="hl">20,000+ skins</span><br>with smart filters</div>`, `
<div style="background:${C.surface};padding:12px 16px 12px;">
  <div style="display:flex;gap:9px;align-items:center;">
    <div style="flex:1;display:flex;align-items:center;gap:9px;background:${C.elevated};border:1px solid ${C.divider};border-radius:12px;padding:10px 13px;">
      ${icon(D.search, 17, C.text2)}<span style="font-size:14px;">asiimov</span><span style="width:2px;height:16px;background:${C.orange};"></span>
    </div>
    <div style="width:42px;height:42px;border-radius:12px;background:${C.orange};display:flex;align-items:center;justify-content:center;">${icon(D.filter, 19, '#fff')}</div>
  </div>
  <div style="display:flex;gap:7px;margin-top:11px;">
    <span style="font-size:11.5px;font-weight:600;background:rgba(255,107,53,.18);color:${C.orange};border:1px solid ${C.orange};padding:4px 12px;border-radius:14px;">Covert</span>
    <span style="font-size:11.5px;font-weight:600;background:rgba(255,107,53,.18);color:${C.orange};border:1px solid ${C.orange};padding:4px 12px;border-radius:14px;">Field-Tested</span>
    <span style="font-size:11.5px;font-weight:600;color:${C.text2};border:1px solid ${C.divider};padding:4px 12px;border-radius:14px;">StatTrak™</span>
    <span style="font-size:11.5px;font-weight:600;color:${C.text2};border:1px solid ${C.divider};padding:4px 12px;border-radius:14px;">Price</span>
  </div>
</div>
<div style="padding:12px 18px 8px;font-size:12.5px;color:${C.text2};">128 results</div>
${searchCard(IMG.awpAsiimov, 'AWP | Asiimov', 'FT', 'Covert', C.covert, '$154.16', '+7.2%')}
${searchCard(IMG.akAsiimov, 'AK-47 | Asiimov', 'FT', 'Covert', C.covert, '$182.40', '+12.4%')}
${searchCard(IMG.printstream, 'M4A1-S | Printstream', 'FT', 'Covert', C.covert, '$318.20', '+2.1%')}
${searchCard(IMG.m4Neonoir, 'M4A4 | Neo-Noir', 'FT', 'Covert', C.covert, '$38.94', '−0.8%', false)}
${searchCard(IMG.howl, 'M4A4 | Howl', 'FT', 'Contraband', C.contraband, '$4,310.00', '+1.6%')}
${bottomNav('Search')}`)

// =============== FEATURE GRAPHIC 1024x500 ===============
const featureGraphic = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><style>
* { margin:0; padding:0; box-sizing:border-box; }
html,body { width:1024px; height:500px; overflow:hidden; }
body {
  font-family:'Segoe UI', Roboto, sans-serif; color:${C.text}; position:relative;
  background:
    radial-gradient(ellipse 700px 500px at 82% 50%, rgba(255,107,53,.22), transparent 70%),
    radial-gradient(ellipse 500px 400px at 10% 100%, rgba(88,166,255,.10), transparent 70%),
    linear-gradient(120deg, #10151d 0%, #0D1117 100%);
  display:flex; align-items:center;
}
</style></head><body>
<div style="padding-left:64px; max-width:560px; position:relative; z-index:2;">
  <div style="font-size:40px;font-weight:800;letter-spacing:-.5px;"><span style="color:${C.orange};">CS2</span> SkinFlip</div>
  <div style="font-size:23px;font-weight:600;color:${C.text};margin-top:14px;line-height:1.35;">Track skin prices, compare marketplaces &amp; never miss a deal</div>
  <div style="display:flex;gap:10px;margin-top:22px;">
    ${['Price alerts', 'Top movers', 'Portfolio'].map(t => `<span style="font-size:15px;font-weight:600;color:${C.orange};background:rgba(255,107,53,.13);border:1px solid rgba(255,107,53,.5);padding:7px 16px;border-radius:20px;">${t}</span>`).join('')}
  </div>
</div>
<img src="${IMG.awpAsiimov}" style="position:absolute;right:150px;top:60px;width:400px;transform:rotate(-8deg);filter:drop-shadow(0 20px 40px rgba(0,0,0,.55));z-index:1;">
<img src="${IMG.butterflyDoppler}" style="position:absolute;right:40px;bottom:30px;width:230px;transform:rotate(14deg);filter:drop-shadow(0 16px 32px rgba(0,0,0,.5));z-index:1;">
</body></html>`

// =============== FEATURE GRAPHIC v2 (hybrid: icon-led layout, brand palette) ===============
const featureGraphicV2 = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><style>
* { margin:0; padding:0; box-sizing:border-box; }
html,body { width:1024px; height:500px; overflow:hidden; }
body {
  font-family:'Segoe UI', Roboto, sans-serif; color:${C.text}; position:relative;
  background:
    radial-gradient(ellipse 620px 520px at 79% 50%, rgba(63,185,80,.14), transparent 68%),
    radial-gradient(ellipse 700px 500px at 12% 10%, rgba(255,107,53,.13), transparent 70%),
    linear-gradient(120deg, #12181f 0%, #0D1117 100%);
  display:flex; align-items:center; justify-content:space-between;
}
</style></head><body>
<div style="padding-left:66px; max-width:600px;">
  <div style="display:flex;align-items:center;gap:10px;margin-bottom:20px;">
    <span style="width:10px;height:10px;border-radius:50%;background:${C.orange};box-shadow:0 0 10px ${C.orange};"></span>
    <span style="font-size:17px;font-weight:700;letter-spacing:3px;color:${C.text2};">CS2 SKINFLIP</span>
  </div>
  <div style="font-size:46px;font-weight:800;line-height:1.14;letter-spacing:-.5px;white-space:nowrap;">Track market prices<br>for <span style="color:${C.orange};">CS2 skins</span></div>
  <div style="display:flex;gap:11px;margin-top:26px;">
    ${['Price alerts', 'Top movers', 'Portfolio'].map(t =>
      `<span style="font-size:16px;font-weight:600;color:${C.text};background:rgba(255,255,255,.06);border:1px solid ${C.divider};padding:8px 18px;border-radius:22px;display:flex;align-items:center;gap:8px;"><span style="width:8px;height:8px;border-radius:50%;background:${C.orange};"></span>${t}</span>`).join('')}
  </div>
</div>
<div style="padding-right:84px;">
  <div style="width:308px;height:308px;border-radius:70px;overflow:hidden;box-shadow:0 24px 60px rgba(0,0,0,.65), 0 0 90px rgba(63,185,80,.28);">
    <img src="icon.png" style="width:100%;height:100%;object-fit:cover;">
  </div>
</div>
</body></html>`

// ---- write files ----
const files = {
  'feature-graphic-v2.html': featureGraphicV2,
  '01-home.html': homeScreen,
  '02-detail.html': detailScreen,
  '03-alerts.html': alertsScreen,
  '04-history.html': historyScreen,
  '05-portfolio.html': portfolioScreen,
  '06-search.html': searchScreen,
  'feature-graphic.html': featureGraphic,
}
for (const [name, html] of Object.entries(files)) writeFileSync(join(OUT, name), html)
console.log('Wrote', Object.keys(files).length, 'files to', OUT)
