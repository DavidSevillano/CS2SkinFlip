import { env } from '../config/env'

// Lazy-initialise Resend so it only loads when RESEND_API_KEY is set
let resend: import('resend').Resend | null = null

async function getResend() {
  if (resend) return resend
  if (!env.RESEND_API_KEY) return null

  const { Resend } = await import('resend')
  resend = new Resend(env.RESEND_API_KEY)
  return resend
}

async function sendEmail(to: string, subject: string, html: string): Promise<void> {
  const client = await getResend()
  if (!client) {
    console.warn(`[Email] RESEND_API_KEY not set — skipping email to ${to}: "${subject}"`)
    return
  }

  try {
    await client.emails.send({ from: env.EMAIL_FROM, to, subject, html })
  } catch (e) {
    console.warn('[Email] Failed to send:', e)
  }
}

export async function sendPasswordResetEmail(to: string, resetUrl: string): Promise<void> {
  await sendEmail(
    to,
    'Reset your CS2 SkinFlip password',
    `
      <p>We received a request to reset your CS2 SkinFlip password.</p>
      <p><a href="${resetUrl}">Click here to choose a new password</a>. This link expires in 15 minutes.</p>
      <p>If you didn't request this, you can safely ignore this email.</p>
    `,
  )
}

export async function sendVerificationEmail(to: string, verifyUrl: string): Promise<void> {
  await sendEmail(
    to,
    'Confirm your CS2 SkinFlip account',
    `
      <p>Welcome to CS2 SkinFlip!</p>
      <p><a href="${verifyUrl}">Click here to confirm your email address</a>. This link expires in 24 hours.</p>
    `,
  )
}
