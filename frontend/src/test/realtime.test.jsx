import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { ToastProvider } from '../components/common/Toast';
import { DiscussionPanel } from '../components/session/DiscussionPanel';
import { PollPanel } from '../components/session/PollPanel';
import { LiveSessionPage } from '../pages/LiveSessionPage';
import { api, getAccessToken } from '../api/client';

vi.mock('../api/client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
  setAccessToken: vi.fn(),
  getAccessToken: vi.fn(() => 'test-jwt-token'),
}));

// Mock @stomp/stompjs
vi.mock('@stomp/stompjs', () => {
  return {
    Client: vi.fn().mockImplementation(() => ({
      activate: vi.fn(),
      deactivate: vi.fn(),
      publish: vi.fn(),
      subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })),
    })),
  };
});

describe('Real-time Polls and Discussion Frontend Flows', () => {
  const teacherUser = {
    id: 1,
    email: 'teacher@school.edu',
    fullName: 'Dr. Turing',
    role: 'TEACHER',
  };

  const studentUser = {
    id: 2,
    email: 'student@school.edu',
    fullName: 'Ada Lovelace',
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

  it('renders DiscussionPanel with chat history and sends message via REST fallback when STOMP is null', async () => {
    api.get.mockImplementation((url) => {
      if (url.includes('/discussion')) {
        return Promise.resolve({
          data: {
            content: [
              {
                id: 101,
                sessionId: 1,
                senderId: 1,
                senderName: 'Dr. Turing',
                senderRole: 'TEACHER',
                content: 'Welcome to algorithm design!',
                createdAt: new Date().toISOString(),
              },
            ],
            totalElements: 1,
          },
        });
      }
      return Promise.reject(new Error('Unknown url'));
    });

    api.post.mockResolvedValueOnce({
      data: {
        id: 102,
        sessionId: 1,
        senderId: 2,
        senderName: 'Ada Lovelace',
        senderRole: 'STUDENT',
        content: 'Excited to be here!',
        createdAt: new Date().toISOString(),
      },
    });

    renderWithProviders(
      <DiscussionPanel
        sessionId={1}
        stompClient={null}
        isConnected={false}
        user={studentUser}
      />,
      { user: studentUser }
    );

    // Initial message is displayed
    await waitFor(() => {
      expect(screen.getByText('Welcome to algorithm design!')).toBeInTheDocument();
      expect(screen.getByText('Dr. Turing')).toBeInTheDocument();
    });

    // Student types and sends a message
    const input = screen.getByPlaceholderText(/Type a message.../i);
    fireEvent.change(input, { target: { value: 'Excited to be here!' } });

    const form = input.closest('form');
    fireEvent.submit(form);

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/sessions/1/discussion', {
        content: 'Excited to be here!',
      });
      expect(screen.getByText('Excited to be here!')).toBeInTheDocument();
    });
  });

  it('renders PollPanel with open poll and allows student to cast vote', async () => {
    const mockPoll = {
      id: 50,
      sessionId: 1,
      question: 'Which sort algorithm has best average time complexity?',
      status: 'OPEN',
      resultsVisibleToStudents: false,
      totalVotes: null, // hidden from student
      userVotedOptionId: null,
      options: [
        { id: 201, optionText: 'QuickSort O(n log n)', orderIndex: 0, voteCount: null, percentage: null },
        { id: 202, optionText: 'BubbleSort O(n^2)', orderIndex: 1, voteCount: null, percentage: null },
      ],
      createdAt: new Date().toISOString(),
    };

    api.get.mockImplementation((url) => {
      if (url.includes('/polls')) {
        return Promise.resolve({ data: [mockPoll] });
      }
      return Promise.reject(new Error('Unknown url'));
    });

    api.post.mockResolvedValueOnce({
      data: {
        ...mockPoll,
        userVotedOptionId: 201,
      },
    });

    renderWithProviders(
      <PollPanel
        sessionId={1}
        stompClient={null}
        isConnected={false}
        user={studentUser}
        isTeacherOrAdmin={false}
      />,
      { user: studentUser }
    );

    await waitFor(() => {
      expect(screen.getByText('Which sort algorithm has best average time complexity?')).toBeInTheDocument();
      expect(screen.getByText('QuickSort O(n log n)')).toBeInTheDocument();
      expect(screen.getByText('Submit Vote')).toBeInTheDocument();
    });

    // Select option 1
    const option1 = screen.getByText('QuickSort O(n log n)');
    fireEvent.click(option1);

    // Click submit vote
    const submitBtn = screen.getByText('Submit Vote');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith('/sessions/1/polls/50/vote', {
        optionId: 201,
      });
    });
  });

  it('renders LiveSessionPage with 503 Jitsi unconfigured state, leave button, and real tabs', async () => {
    api.get.mockImplementation((url) => {
      if (url.includes('/sessions/1/discussion')) {
        return Promise.resolve({ data: { content: [] } });
      }
      if (url.includes('/sessions/1/polls')) {
        return Promise.resolve({ data: [] });
      }
      if (url.includes('/sessions/1')) {
        return Promise.resolve({
          data: {
            id: 1,
            title: 'CS 101 Lecture',
            classroomName: 'Computer Science',
            status: 'ACTIVE',
          },
        });
      }
      return Promise.reject(new Error('Unknown url'));
    });

    // Jitsi join returns 503 INTEGRATION_NOT_CONFIGURED
    api.post.mockImplementation((url) => {
      if (url.includes('/join')) {
        const error = new Error('Integration not configured');
        error.response = {
          status: 503,
          data: {
            code: 'INTEGRATION_NOT_CONFIGURED',
            message: 'Jitsi JaaS credentials not configured in environment',
          },
        };
        return Promise.reject(error);
      }
      return Promise.resolve({ data: {} });
    });

    render(
      <ToastProvider>
        <AuthContext.Provider value={{ user: teacherUser, token: 'mock-jwt', logout: vi.fn() }}>
          <MemoryRouter initialEntries={['/sessions/1/live']}>
            <Routes>
              <Route path="/sessions/:id/live" element={<LiveSessionPage />} />
            </Routes>
          </MemoryRouter>
        </AuthContext.Provider>
      </ToastProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('CS 101 Lecture')).toBeInTheDocument();
      expect(screen.getByText(/Live Video Integration Not Configured/i)).toBeInTheDocument();
      expect(screen.getByText('Live Chat')).toBeInTheDocument();
      expect(screen.getByText('Polls')).toBeInTheDocument();
    });
  });
});
