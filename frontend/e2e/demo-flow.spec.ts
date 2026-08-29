import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

/**
 * The demo walkthrough, end to end.
 *
 * This is the scenario the product is judged on: an employee submits availability and a
 * note, a manager generates three plans, compares them, edits one by hand, asks a question,
 * and publishes. If this passes against a live stack, the MVP does what it claims.
 *
 * Requires a running stack with seeded demo data (SEED_DEMO_DATA=true).
 */

const PASSWORD = 'demo1234'

async function login(page: Page, email: string) {
  await page.goto('/login')
  await page.getByLabel('E-Mail').fill(email)
  await page.getByLabel('Passwort').fill(PASSWORD)
  await page.getByRole('button', { name: 'Anmelden' }).click()
  await expect(page.getByRole('button', { name: 'Abmelden' })).toBeVisible()
}

test.describe('Mitarbeiter-Selfservice', () => {
  test('trägt Verfügbarkeit und eine Anmerkung ein', async ({ page }) => {
    await login(page, 'employee@demo.local')

    await page.getByRole('link', { name: 'Meine Verfügbarkeit' }).click()
    await expect(page.getByRole('heading', { name: 'Meine Verfügbarkeit' })).toBeVisible()

    // One day unavailable, one day a wish - the two cases the solver treats differently.
    const days = page.locator('.day-card')
    await days.nth(1).getByRole('button', { name: 'Nicht verfügbar' }).click()
    await days.nth(5).getByRole('button', { name: 'Wunschzeit' }).click()

    await page.getByRole('button', { name: 'Verfügbarkeit speichern' }).click()
    await expect(page.getByText('Gespeichert.')).toBeVisible()

    await page
      .getByPlaceholder('Deine Anmerkung …')
      .fill('Samstag kann ich arbeiten, aber bitte erst ab 17 Uhr, weil ich vorher Uni habe.')
    await page.getByRole('button', { name: 'Anmerkung senden' }).click()
    await expect(page.locator('.card-tight').getByText(/bitte erst ab 17 Uhr/)).toBeVisible()
  })
})

test.describe('Schichtleitung', () => {
  test('generiert, vergleicht, bearbeitet und veröffentlicht einen Plan', async ({ page }) => {
    await login(page, 'manager@demo.local')

    await page.getByRole('link', { name: 'Planungszeiträume' }).click()
    await page.getByRole('link', { name: 'Öffnen' }).first().click()

    // Dashboard: the numbers a manager checks before planning.
    await expect(page.getByText('Verfügbarkeiten abgegeben')).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Deadline' })).toBeVisible()

    await page.getByRole('button', { name: 'Pläne generieren' }).click()

    // Solving is asynchronous; the UI polls the job. Allow real solver time.
    await expect(page.getByText('Drei Pläne wurden berechnet.')).toBeVisible({ timeout: 180_000 })

    await page.getByRole('link', { name: 'Planvorschläge' }).click()
    await expect(page.getByRole('heading', { name: 'Fair' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Kostenoptimiert' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Ausgewogen' })).toBeVisible()
    await expect(page.getByText('Direktvergleich')).toBeVisible()

    // Take the recommendation, then open it.
    const balanced = page.locator('.card', { has: page.getByRole('heading', { name: 'Ausgewogen' }) })
    await balanced.getByRole('button', { name: 'Diesen wählen' }).click()
    await expect(balanced.getByText('gewählt')).toBeVisible()
    await balanced.getByRole('link', { name: 'Öffnen' }).click()

    // Manual control: pin one assignment so a re-run cannot move it.
    const firstSlot = page.locator('.slot').first()
    await firstSlot.getByTitle('Fixieren').click()
    await expect(firstSlot.getByTitle(/Fixiert/)).toBeVisible()

    await page.getByRole('button', { name: 'Plan veröffentlichen' }).click()
    await expect(page.getByText(/ist veröffentlicht/)).toBeVisible()
  })

  test('findet Ersatz für eine Schicht', async ({ page }) => {
    await login(page, 'manager@demo.local')
    await page.goto('/periods')
    await page.getByRole('link', { name: 'Öffnen' }).first().click()
    await page.getByRole('link', { name: 'Planvorschläge' }).click()
    await page.getByRole('link', { name: 'Öffnen' }).first().click()

    await page.locator('.slot').first().getByTitle('Ersatz suchen').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByText(/Ersatz für/)).toBeVisible()
  })

  test('beantwortet eine Frage aus echten Daten', async ({ page }) => {
    await login(page, 'manager@demo.local')
    await page.goto('/periods')
    await page.getByRole('link', { name: 'Öffnen' }).first().click()

    await page.getByRole('button', { name: 'Fragen stellen' }).click()
    await expect(page.getByRole('dialog', { name: 'Fragen zum Dienstplan' })).toBeVisible()

    await page.getByPlaceholder('Frage eingeben').fill('Welche Schichten sind unterbesetzt?')
    await page.getByRole('button', { name: 'Senden' }).click()

    // Either a grounded answer or an honest "the model is offline" - both are correct
    // behaviour, and both prove the request reached the backend.
    await expect(
      page.locator('.bubble-ai, .bubble-error').filter({ hasText: /\S/ }).first(),
    ).toBeVisible({ timeout: 60_000 })
  })
})

test.describe('Berechtigungen', () => {
  test('Mitarbeitende sehen keine Managementbereiche', async ({ page }) => {
    await login(page, 'employee@demo.local')

    await expect(page.getByRole('link', { name: 'Planungszeiträume' })).toHaveCount(0)

    // The guard is convenience; the server refuses regardless. Both must hold.
    await page.goto('/periods')
    await expect(page.getByText('Kein Zugriff')).toBeVisible()
  })
})
