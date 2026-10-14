import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { mockFetch } from '../test/fetchMock'
import { renderAs } from '../test/renderAs'
import { DrugsPage } from './DrugsPage'

const drug = { id: 1, name: 'Amoxicillin', dosage: '500 mg', category: 'Antibiotic (penicillin)', notes: null }

describe('DrugsPage', () => {
  it('lets an admin add, edit and delete drugs', async () => {
    mockFetch({ 'GET /api/drugs': { body: [drug] } })
    renderAs('ADMIN', <DrugsPage />)

    expect(await screen.findByText('Amoxicillin')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add drug' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument()
  })

  it('shows drugs read-only to a clinician', async () => {
    mockFetch({ 'GET /api/drugs': { body: [drug] } })
    renderAs('CLINICIAN', <DrugsPage />)

    expect(await screen.findByText('Amoxicillin')).toBeInTheDocument()
    expect(screen.getByText('Read-only: only administrators can change the knowledge base.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add drug' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })
})
