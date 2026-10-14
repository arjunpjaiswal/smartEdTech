import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/client';
import { Card } from '../components/common/Card';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorState } from '../components/common/ErrorState';
import { Skeleton } from '../components/common/Skeleton';
import { Button } from '../components/common/Button';
import {
  BookOpen,
  Calendar,
  Users,
  FileText,
  Plus,
  ArrowRight,
  Video,
  CheckCircle2,
  AlertCircle,
  Clock,
} from 'lucide-react';

export const DashboardPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [summary, setSummary] = useState(null);
  const [loadingSummary, setLoadingSummary] = useState(true);
  const [summaryError, setSummaryError] = useState(null);

  const [integrations, setIntegrations] = useState(null);
  const [loadingIntegrations, setLoadingIntegrations] = useState(false);
  const [integrationError, setIntegrationError] = useState(null);

  const fetchSummary = useCallback(async () => {
    setLoadingSummary(true);
    setSummaryError(null);
    try {
      const response = await api.get('/dashboard/summary');
      setSummary(response.data);
    } catch (err) {
      setSummaryError(err.response?.data?.message || 'Failed to load dashboard data.');
    } finally {
      setLoadingSummary(false);
    }
  }, []);

  const fetchIntegrations = useCallback(async () => {
    if (user?.role !== 'ADMIN' && user?.role !== 'TEACHER') return;

    setLoadingIntegrations(true);
    setIntegrationError(null);
    try {
      const response = await api.get('/system/integrations');
      setIntegrations(response.data);
    } catch (err) {
      setIntegrationError(err.response?.data?.message || 'Failed to load system integration statuses.');
    } finally {
      setLoadingIntegrations(false);
    }
  }, [user]);

  useEffect(() => {
    fetchSummary();
    fetchIntegrations();
  }, [fetchSummary, fetchIntegrations]);

  return (
    <div className="space-y-6">
      {/* Welcome Header */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">
            Welcome back, {user?.fullName}
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Logged in as <span className="font-semibold text-indigo-600">{user?.role}</span> ({user?.email})
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="primary" icon={BookOpen} onClick={() => navigate('/classrooms')}>
            View Classrooms
          </Button>
          <Button variant="secondary" icon={Calendar} onClick={() => navigate('/sessions')}>
            Live Sessions
          </Button>
        </div>
      </div>

      {/* Aggregate Metric Cards */}
      {loadingSummary ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : summaryError ? (
        <ErrorState
          title="Dashboard Summary Error"
          message={summaryError}
          onRetry={fetchSummary}
        />
      ) : summary ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard label={summary.stat1Label} value={summary.stat1} icon={BookOpen} color="indigo" />
          <StatCard label={summary.stat2Label} value={summary.stat2} icon={Users} color="emerald" />
          <StatCard label={summary.stat3Label} value={summary.stat3} icon={Calendar} color="amber" />
          <StatCard label={summary.stat4Label} value={summary.stat4} icon={FileText} color="blue" />
        </div>
      ) : null}

      {/* Teacher & Admin: System Integrations Status Card */}
      {(user?.role === 'ADMIN' || user?.role === 'TEACHER') && (
        <Card
          title="AI & Live Classroom Integrations"
          subtitle="Real-time provider readiness reported by the backend API"
        >
          {loadingIntegrations ? (
            <div className="space-y-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : integrationError ? (
            <ErrorState
              title="Integration Status Error"
              message={integrationError}
              onRetry={fetchIntegrations}
            />
          ) : integrations ? (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <IntegrationItem
                name="Google Gemini"
                description="PDF extraction & generation"
                status={integrations.gemini}
              />
              <IntegrationItem
                name="Groq LLM"
                description="Fast text processing"
                status={integrations.groq}
              />
              <IntegrationItem
                name="Jitsi Meet (JaaS)"
                description="Live video classes"
                status={integrations.jitsi}
              />
            </div>
          ) : null}
        </Card>
      )}

      {/* Upcoming Sessions & Recent Classrooms */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Upcoming Sessions */}
        <Card
          title="Upcoming Live Sessions"
          subtitle="Scheduled video classes"
          action={
            <button
              onClick={() => navigate('/sessions')}
              className="text-xs text-indigo-600 hover:text-indigo-800 font-semibold flex items-center gap-1"
            >
              View all <ArrowRight className="w-3.5 h-3.5" />
            </button>
          }
        >
          {loadingSummary ? (
            <div className="space-y-3">
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
            </div>
          ) : !summary?.upcomingSessions || summary.upcomingSessions.length === 0 ? (
            <EmptyState
              icon={Calendar}
              title="No upcoming live sessions"
              description="There are currently no scheduled live lectures or video sessions."
            />
          ) : (
            <div className="divide-y divide-slate-100">
              {summary.upcomingSessions.map((session) => (
                <div key={session.id} className="py-3 flex items-center justify-between gap-3">
                  <div>
                    <h4 className="text-sm font-semibold text-slate-900">{session.title}</h4>
                    <p className="text-xs text-slate-500 mt-0.5">{session.classroomName}</p>
                    <div className="flex items-center gap-1 text-[11px] text-slate-400 mt-1">
                      <Clock className="w-3 h-3" />
                      <span>{new Date(session.scheduledStartTime).toLocaleString()}</span>
                    </div>
                  </div>
                  <Button
                    size="sm"
                    variant="primary"
                    icon={Video}
                    onClick={() => navigate(`/sessions/${session.id}/live`)}
                  >
                    Join
                  </Button>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Recent Classrooms */}
        <Card
          title={user?.role === 'STUDENT' ? 'Enrolled Classrooms' : 'Your Classrooms'}
          subtitle="Active academic spaces"
          action={
            <button
              onClick={() => navigate('/classrooms')}
              className="text-xs text-indigo-600 hover:text-indigo-800 font-semibold flex items-center gap-1"
            >
              View all <ArrowRight className="w-3.5 h-3.5" />
            </button>
          }
        >
          {loadingSummary ? (
            <div className="space-y-3">
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
            </div>
          ) : !summary?.classrooms || summary.classrooms.length === 0 ? (
            <EmptyState
              icon={BookOpen}
              title="No classrooms found"
              description={
                user?.role === 'STUDENT'
                  ? 'You are not enrolled in any classrooms yet. Use an invite code to join.'
                  : 'You have not created any classrooms yet. Get started by creating your first space.'
              }
              action={
                <Button
                  variant="primary"
                  size="sm"
                  icon={Plus}
                  onClick={() => navigate('/classrooms')}
                >
                  {user?.role === 'STUDENT' ? 'Join Classroom' : 'Create Classroom'}
                </Button>
              }
            />
          ) : (
            <div className="divide-y divide-slate-100">
              {summary.classrooms.map((cls) => (
                <div
                  key={cls.id}
                  onClick={() => navigate(`/classrooms/${cls.id}`)}
                  className="py-3 flex items-center justify-between cursor-pointer hover:bg-slate-50/80 px-2 rounded-lg transition-colors"
                >
                  <div>
                    <h4 className="text-sm font-semibold text-slate-900">{cls.name}</h4>
                    <p className="text-xs text-slate-500 mt-0.5 line-clamp-1">{cls.description || 'No description'}</p>
                    <span className="text-[11px] text-indigo-600 font-medium mt-1 inline-block">
                      {cls.studentCount} students enrolled
                    </span>
                  </div>
                  <ArrowRight className="w-4 h-4 text-slate-400" />
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
};

const StatCard = ({ label, value, icon: Icon, color }) => {
  const colorMap = {
    indigo: 'bg-indigo-50 text-indigo-600',
    emerald: 'bg-emerald-50 text-emerald-600',
    amber: 'bg-amber-50 text-amber-600',
    blue: 'bg-blue-50 text-blue-600',
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm flex items-center gap-4">
      <div className={`w-12 h-12 rounded-xl flex items-center justify-center font-bold ${colorMap[color] || colorMap.indigo}`}>
        <Icon className="w-6 h-6" />
      </div>
      <div>
        <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">{label}</p>
        <p className="text-2xl font-bold text-slate-900 mt-0.5">{value}</p>
      </div>
    </div>
  );
};

const IntegrationItem = ({ name, description, status }) => {
  const isConfigured = status === 'CONFIGURED';

  return (
    <div className="p-4 rounded-xl border border-slate-200 bg-slate-50/50 flex items-start justify-between">
      <div>
        <h4 className="text-sm font-semibold text-slate-900">{name}</h4>
        <p className="text-xs text-slate-500 mt-0.5">{description}</p>
      </div>
      <span
        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border ${
          isConfigured
            ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
            : 'bg-amber-50 text-amber-700 border-amber-200'
        }`}
      >
        {isConfigured ? (
          <CheckCircle2 className="w-3.5 h-3.5" />
        ) : (
          <AlertCircle className="w-3.5 h-3.5" />
        )}
        {status}
      </span>
    </div>
  );
};
