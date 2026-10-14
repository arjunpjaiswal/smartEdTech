import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { ToastProvider } from '../components/common/Toast';
import { AssessmentsPage } from '../pages/AssessmentsPage';
import { AiGeneratorModal } from '../components/assessment/AiGeneratorModal';
import { api } from '../api/client';

vi.mock('../api/client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
  setAccessToken: vi.fn(),
  getAccessToken: vi.fn(),
}));

describe('Assessment Frontend Flows', () => {
  const teacherUser = {
    id: 1,
    email: 'teacher@example.com',
    fullName: 'Professor Smith',
    role: 'TEACHER',
  };

  const studentUser = {
    id: 2,
    email: 'student@example.com',
    fullName: 'Jane Student',
    role: 'STUDENT',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderWithProviders = (ui, authValue = { user: teacherUser }) => {
    return render(
      <ToastProvider>
        <AuthContext.Provider value={{ ...authValue, token: 'mock-jwt', logout: vi.fn() }}>
          <MemoryRouter>{ui}</MemoryRouter>
        </AuthContext.Provider>
      </ToastProvider>
    );
  };

  it('renders AssessmentsPage with empty state for a classroom without assessments', async () => {
    api.get.mockImplementation((url) => {
      if (url.includes('/classrooms?')) {
        return Promise.resolve({
          data: { content: [{ id: 1, name: 'Physics 101' }] },
        });
      }
      if (url.includes('/assessments?')) {
        return Promise.resolve({
          data: { content: [] },
        });
      }
      return Promise.resolve({ data: {} });
    });

    renderWithProviders(<AssessmentsPage />, { user: teacherUser });

    await waitFor(() => {
      expect(screen.getByText('No Assessments Yet')).toBeInTheDocument();
    });
  });

  it('renders published assessments and allows student to view them', async () => {
    api.get.mockImplementation((url) => {
      if (url.includes('/classrooms?')) {
        return Promise.resolve({
          data: { content: [{ id: 1, name: 'Physics 101' }] },
        });
      }
      if (url.includes('/assessments?')) {
        return Promise.resolve({
          data: {
            content: [
              {
                id: 10,
                title: 'Kinematics Quiz 1',
                description: 'Introductory physics quiz',
                durationMinutes: 30,
                totalMarks: 10.0,
                questionCount: 2,
                maxAttempts: 1,
                status: 'PUBLISHED',
              },
            ],
          },
        });
      }
      if (url.includes('/attempts')) {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: {} });
    });

    renderWithProviders(<AssessmentsPage />, { user: studentUser });

    await waitFor(() => {
      expect(screen.getByText('Kinematics Quiz 1')).toBeInTheDocument();
      expect(screen.getByText('Start Assessment')).toBeInTheDocument();
    });
  });

  it('renders AiGeneratorModal and displays 503 error message when integration not configured', async () => {
    api.get.mockImplementation((url) => {
      if (url.includes('/materials')) {
        return Promise.resolve({
          data: { content: [{ id: 101, title: 'Chapter 1 Notes.pdf', originalFilename: 'notes.pdf' }] },
        });
      }
      return Promise.resolve({ data: {} });
    });

    api.post.mockRejectedValueOnce({
      response: {
        status: 503,
        data: {
          code: 'INTEGRATION_NOT_CONFIGURED',
          message: 'AI generation is not configured. Missing: GEMINI_API_KEY or GROQ_API_KEY environment variables.',
        },
      },
    });

    renderWithProviders(
      <AiGeneratorModal
        isOpen={true}
        onClose={vi.fn()}
        classroomId={1}
        assessmentId={10}
      />,
      { user: teacherUser }
    );

    await waitFor(() => {
      expect(screen.getByText('Source Study Material')).toBeInTheDocument();
    });

    const generateBtn = screen.getByText('Generate Questions');
    fireEvent.click(generateBtn);

    await waitFor(() => {
      expect(screen.getByText('AI Generation Failed')).toBeInTheDocument();
      expect(screen.getByText(/Missing: GEMINI_API_KEY or GROQ_API_KEY/i)).toBeInTheDocument();
    });
  });
});
