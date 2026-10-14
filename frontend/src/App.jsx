import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './components/common/Toast';
import { ProtectedRoute } from './components/auth/ProtectedRoute';
import { AppShell } from './components/layout/AppShell';

import { LandingPage } from './pages/LandingPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { DashboardPage } from './pages/DashboardPage';
import { ClassroomsPage } from './pages/ClassroomsPage';
import { ClassroomDetailPage } from './pages/ClassroomDetailPage';
import { SessionsPage } from './pages/SessionsPage';
import { LiveSessionPage } from './pages/LiveSessionPage';
import { AssessmentsPage } from './pages/AssessmentsPage';
import { AssessmentBuilderPage } from './pages/AssessmentBuilderPage';
import { AssessmentTakingPage } from './pages/AssessmentTakingPage';
import { AssessmentResultPage } from './pages/AssessmentResultPage';
import { AssessmentAttemptsListPage } from './pages/AssessmentAttemptsListPage';
import { AdminPage } from './pages/AdminPage';
import { UnauthorizedPage } from './pages/UnauthorizedPage';
import { NotFoundPage } from './pages/NotFoundPage';

export const App = () => {
  return (
    <ToastProvider>
      <AuthProvider>
        <Routes>
          {/* Public Routes */}
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/unauthorized" element={<UnauthorizedPage />} />

          {/* Protected Routes wrapped in AppShell */}
          <Route
            element={
              <ProtectedRoute>
                <AppShell />
              </ProtectedRoute>
            }
          >
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/classrooms" element={<ClassroomsPage />} />
            <Route path="/classrooms/:id" element={<ClassroomDetailPage />} />
            <Route path="/sessions" element={<SessionsPage />} />
            <Route path="/sessions/:id/live" element={<LiveSessionPage />} />

            {/* Assessment Routes */}
            <Route path="/assessments" element={<AssessmentsPage />} />
            <Route
              path="/assessments/:id/builder"
              element={
                <ProtectedRoute allowedRoles={['TEACHER', 'ADMIN']}>
                  <AssessmentBuilderPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/assessments/:id/take"
              element={
                <ProtectedRoute allowedRoles={['STUDENT', 'ADMIN']}>
                  <AssessmentTakingPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/assessments/:id/attempts"
              element={
                <ProtectedRoute allowedRoles={['TEACHER', 'ADMIN']}>
                  <AssessmentAttemptsListPage />
                </ProtectedRoute>
              }
            />
            <Route path="/attempts/:attemptId/results" element={<AssessmentResultPage />} />

            <Route
              path="/study-buddy"
              element={
                <ProtectedRoute allowedRoles={['STUDENT']}>
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin"
              element={
                <ProtectedRoute allowedRoles={['ADMIN']}>
                  <AdminPage />
                </ProtectedRoute>
              }
            />
          </Route>

          {/* Catch-all 404 route */}
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </ToastProvider>
  );
};

export default App;
