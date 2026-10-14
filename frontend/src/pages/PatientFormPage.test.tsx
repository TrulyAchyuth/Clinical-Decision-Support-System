import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it } from 'vitest'
import { mockFetch } from '../test/fetchMock'
import { PatientFormPage } from './PatientFormPage'

function renderNewPatientForm() {
  return render(
    <MemoryRouter initialEntries={['/patients/new']}>
      <Routes>
        <Route path="/patients/new" element={<PatientFormPage />} />
        <Route path="/patients/:id" element={<p>Patient page</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('PatientFormPage', () => {
  it('sends empty required fields as null and shows each backend field error under its field', async () => {
    const fetchMock = mockFetch({
      'POST /api/patients': {
        status: 400,
        body: { status: 400, detail: 'Validation failed', errors: { fullName: 'must not be blank', dateOfBirth: 'must not be null', gender: 'must not be null' } },
      },
    })
    renderNewPatientForm()

    await userEvent.click(screen.getByRole('button', { name: 'Save patient' }))

    expect(await screen.findByText('Validation failed')).toBeInTheDocument()
    // Under the fields:
    expect(screen.getByText('must not be blank')).toHaveClass('text-red-700')
    expect(screen.getAllByText('must not be null')).toHaveLength(2)
    // And listed in the error summary, field by field:
    const summary = screen.getByRole('alert')
    expect(summary).toHaveTextContent('fullName: must not be blank')
    expect(summary).toHaveTextContent('dateOfBirth: must not be null')
    expect(summary).toHaveTextContent('gender: must not be null')
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({ fullName: '', dateOfBirth: null, gender: null })
  })

  it('creates the patient and opens their page', async () => {
    mockFetch({ 'POST /api/patients': { status: 201, body: { id: 12, fullName: 'Sam Sample' } } })
    renderNewPatientForm()

    await userEvent.type(screen.getByLabelText(/Full name/), 'Sam Sample')
    await userEvent.type(screen.getByLabelText(/Date of birth/), '1985-03-14')
    await userEvent.selectOptions(screen.getByLabelText(/Gender/), 'FEMALE')
    await userEvent.click(screen.getByRole('button', { name: 'Save patient' }))

    expect(await screen.findByText('Patient page')).toBeInTheDocument()
  })
})
