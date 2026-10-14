import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { ToastProvider } from '../components/common/Toast';
import { ClassroomsPage } from '../pages/ClassroomsPage';
import { LiveSessionPage } from '../pages/LiveSessionPage';
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

describe('Classroom and Live Session Frontend Flows', () => {
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

  it('renders ClassroomsPage with empty state and allows opening Create Classroom modal for teacher', async () => {
    api.get.mockResolvedValueOnce({ data: { content: [], totalElements: 0 } });

    renderWithProviders(<ClassroomsPage />, { user: teacherUser });

    await waitFor(() => {
      expect(screen.getByText('No Classrooms Created')).toBeInTheDocument();
    });

    const createBtns = screen.getAllByRole('button', { name: /create classroom/i });
    expect(createBtns.length).toBeGreaterThan(0);

    fireEvent.click(createBtns[0]);

    expect(screen.getByText('Create New Classroom')).toBeInTheDocument();
    expect(screen.getByLabelText(/classroom name/i)).toBeInTheDocument();
  });

  it('renders ClassroomsPage with classrooms from API', async () => {
    api.get.mockResolvedValueOnce({
      data: {
        content: [
          {
            id: 101,
            name: 'Linear Algebra',
            description: 'Matrices and vectors',
            joinCode: 'MATH1010',
            teacherId: 1,
            teacherName: 'Professor Smith',
            archived: false,
            studentCount: 25,
          },
        ],
        totalElements: 1,
      },
    });

    renderWithProviders(<ClassroomsPage />, { user: teacherUser });

    await waitFor(() => {
      expect(screen.getByText('Linear Algebra')).toBeInTheDocument();
      expect(screen.getByText('Matrices and vectors')).toBeInTheDocument();
      expect(screen.getByText('25 students')).toBeInTheDocument();
      expect(screen.getByText('MATH1010')).toBeInTheDocument();
    });
  });

  it('renders LiveSessionPage with 503 INTEGRATION_NOT_CONFIGURED message when Jitsi is not configured', async () => {
    api.get.mockResolvedValueOnce({
      data: {
        id: 50,
        classroomId: 101,
        classroomName: 'Linear Algebra',
        title: 'Lecture 1: Vector Spaces',
        status: 'SCHEDULED',
      },
    });

    // /join returns 503 INTEGRATION_NOT_CONFIGURED
    api.post.mockRejectedValueOnce({
      response: {
        status: 503,
        data: {
          code: 'INTEGRATION_NOT_CONFIGURED',
          message: 'Jitsi integration is not configured. Missing: JITSI_APP_ID / JITSI_KID / JITSI_PRIVATE_KEY_PATH',
        },
      },
    });

    render(
      <ToastProvider>
        <AuthContext.Provider value={{ user: studentUser, token: 'mock-jwt', logout: vi.fn() }}>
          <MemoryRouter initialEntries={['/sessions/50/live']}>
            <Routes>
              <Route path="/sessions/:id/live" element={<LiveSessionPage />} />
            </Routes>
          </MemoryRouter>
        </AuthContext.Provider>
      </ToastProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Live Video Integration Not Configured')).toBeInTheDocument();
      expect(
        screen.getByText(/Jitsi integration is not configured. Missing: JITSI_APP_ID/i)
      ).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /retry join/i })).toBeInTheDocument();
    });
  });
});
