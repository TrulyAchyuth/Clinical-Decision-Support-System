import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthState } from './AuthProvider'
import { initials, ProfileMenu } from './ProfileMenu'

function renderMenu(logout = vi.fn().mockResolvedValue(undefined)) {
  const auth: AuthState = {
    user: { username: 'clinician', role: 'CLINICIAN' }, isAdmin: false, sessionEnded: false, checkError: undefined,
    login: vi.fn(), logout,
  }
  render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <p>Outside</p>
        <Routes>
          <Route path="/" element={<ProfileMenu onError={vi.fn()} />} />
          <Route path="/profile" element={<p>Profile page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
  return { logout, button: screen.getByRole('button', { name: 'Account menu for clinician' }) }
}

describe('ProfileMenu', () => {
  it('shows the signed-in username, role and initials', () => {
    const { button } = renderMenu()

    expect(button).toHaveTextContent('clinician')
    expect(button).toHaveTextContent('Clinician')
    expect(button).toHaveTextContent('CL')
    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(initials('jane.doe')).toBe('JD')
  })

  it('opens with the keyboard, moves with arrows and closes on Escape back to the button', async () => {
    const { button } = renderMenu()
    button.focus()
    await userEvent.keyboard('{ArrowDown}')

    expect(screen.getByRole('menu')).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'My profile' })).toHaveFocus()
    await userEvent.keyboard('{ArrowDown}')
    expect(screen.getByRole('menuitem', { name: 'Log out' })).toHaveFocus()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(button).toHaveFocus()
  })

  it('closes when clicking outside', async () => {
    const { button } = renderMenu()
    await userEvent.click(button)
    expect(screen.getByRole('menu')).toBeInTheDocument()

    await userEvent.click(screen.getByText('Outside'))
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('opens My profile and logs out through the auth context', async () => {
    const { button, logout } = renderMenu()
    await userEvent.click(button)
    await userEvent.click(screen.getByRole('menuitem', { name: 'Log out' }))
    expect(logout).toHaveBeenCalled()

    await userEvent.click(button)
    await userEvent.click(screen.getByRole('menuitem', { name: 'My profile' }))
    expect(await screen.findByText('Profile page')).toBeInTheDocument()
  })
})
