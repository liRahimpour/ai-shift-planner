import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import ChatDrawer from './ChatDrawer'

const ask = vi.fn()
vi.mock('@/api/endpoints', () => ({ chatApi: { ask: (...args: unknown[]) => ask(...args) } }))

const PERIOD = '11111111-2222-3333-4444-555555555555'

const renderDrawer = (route = '/periods/' + PERIOD) =>
  render(
    <MemoryRouter initialEntries={[route]}>
      <ChatDrawer onClose={() => {}} />
    </MemoryRouter>,
  )

beforeEach(() => {
  ask.mockReset()
})

describe('ChatDrawer', () => {
  it('sends the planning period from the URL so questions resolve to concrete dates', async () => {
    ask.mockResolvedValue({ answer: 'Sarah und Max.', toolsUsed: [], truncated: false })
    renderDrawer()

    await userEvent.type(screen.getByPlaceholderText(/Frage eingeben/), 'Wer arbeitet Samstag?')
    await userEvent.click(screen.getByRole('button', { name: 'Senden' }))

    await waitFor(() => expect(ask).toHaveBeenCalledWith('Wer arbeitet Samstag?', PERIOD))
    expect(await screen.findByText('Sarah und Max.')).toBeInTheDocument()
  })

  it('sends no period when none is open', async () => {
    ask.mockResolvedValue({ answer: 'ok', toolsUsed: [], truncated: false })
    renderDrawer('/employees')

    await userEvent.type(screen.getByPlaceholderText(/Frage eingeben/), 'Hallo')
    await userEvent.click(screen.getByRole('button', { name: 'Senden' }))

    await waitFor(() => expect(ask).toHaveBeenCalledWith('Hallo', undefined))
  })

  it('shows which backend queries the answer came from', async () => {
    // The point of the chat is that answers are grounded in real data. If the user cannot
    // see the queries behind an answer, they have to take it on trust - which is exactly
    // what this product must not ask of them.
    ask.mockResolvedValue({
      answer: 'Sarah arbeitet Samstagabend an der Bar.',
      toolsUsed: [
        {
          tool: 'getScheduleForDate',
          arguments: { date: '2026-09-12', department: 'Bar' },
          result: '[{"employee":"Sarah"}]',
        },
      ],
      truncated: false,
    })
    renderDrawer()

    await userEvent.type(screen.getByPlaceholderText(/Frage eingeben/), 'Wer?')
    await userEvent.click(screen.getByRole('button', { name: 'Senden' }))

    expect(await screen.findByText(/Datengrundlage/)).toBeInTheDocument()
    expect(screen.getByText(/getScheduleForDate/)).toBeInTheDocument()
    expect(screen.getByText(/date: 2026-09-12/)).toBeInTheDocument()
  })

  it('explains that planning still works when the local model is down', async () => {
    ask.mockRejectedValue(
      new ApiError('AI_TEMPORARILY_UNAVAILABLE', 'unavailable', 503, 'trace-1'),
    )
    renderDrawer()

    await userEvent.type(screen.getByPlaceholderText(/Frage eingeben/), 'Wer arbeitet Samstag?')
    await userEvent.click(screen.getByRole('button', { name: 'Senden' }))

    const message = await screen.findByText(/nicht erreichbar/i)
    expect(message).toHaveTextContent(/Planung, Bearbeitung und Veröffentlichung/)
  })

  it('offers example questions before the first message', async () => {
    renderDrawer()
    expect(screen.getByText('Welche Schichten sind unterbesetzt?')).toBeInTheDocument()
  })

  it('asks a suggested question on click', async () => {
    ask.mockResolvedValue({ answer: 'Keine.', toolsUsed: [], truncated: false })
    renderDrawer()

    await userEvent.click(screen.getByText('Welche Schichten sind unterbesetzt?'))

    await waitFor(() =>
      expect(ask).toHaveBeenCalledWith('Welche Schichten sind unterbesetzt?', PERIOD),
    )
  })
})
