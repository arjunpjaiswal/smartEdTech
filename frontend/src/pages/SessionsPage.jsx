import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/client';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorState } from '../components/common/ErrorState';
import { Skeleton } from '../components/common/Skeleton';
import {
  Calendar,
  Video,
  Clock,
  BookOpen,
} from 'lucide-react';

export const SessionsPage = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchSessions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get('/sessions/upcoming?limit=50');
      setSessions(res.data || []);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load upcoming sessions');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSessions();
  }, [fetchSessions]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Live Video Sessions</h1>
          <p className="text-sm text-slate-500 mt-1">
            Real-time interactive classrooms with video, attendance, and presentation tools
          </p>
        </div>

        <Button variant="secondary" icon={BookOpen} onClick={() => navigate('/classrooms')}>
          Classrooms
        </Button>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <Skeleton className="h-44 w-full" />
          <Skeleton className="h-44 w-full" />
          <Skeleton className="h-44 w-full" />
        </div>
      ) : error ? (
        <ErrorState title="Error Loading Sessions" message={error} onRetry={fetchSessions} />
      ) : sessions.length === 0 ? (
        <EmptyState
          icon={Calendar}
          title="No Upcoming Live Sessions"
          description={
            user?.role === 'STUDENT'
              ? 'No live video sessions are currently scheduled for your enrolled classrooms.'
              : 'You do not have any upcoming live sessions. Go to a classroom to schedule one.'
          }
          action={
            <Button variant="primary" icon={BookOpen} onClick={() => navigate('/classrooms')}>
              Go to Classrooms
            </Button>
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {sessions.map((sess) => (
            <div
              key={sess.id}
              className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm flex flex-col justify-between hover:shadow-md transition-shadow"
            >
              <div>
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-indigo-600 truncate max-w-[180px]">
                    {sess.classroomName}
                  </span>
                  <span
                    className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                      sess.status === 'ACTIVE'
                        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                        : 'bg-indigo-50 text-indigo-700 border border-indigo-200'
                    }`}
                  >
                    {sess.status}
                  </span>
                </div>

                <h3 className="text-base font-bold text-slate-900 mt-2">{sess.title}</h3>
                <p className="text-xs text-slate-500 mt-1 line-clamp-2">{sess.description || 'No description provided.'}</p>

                <div className="flex items-center gap-1.5 text-xs text-slate-400 mt-4">
                  <Clock className="w-3.5 h-3.5" />
                  <span>{new Date(sess.scheduledStartTime).toLocaleString()}</span>
                </div>
              </div>

              <div className="mt-5 pt-4 border-t border-slate-100 flex items-center justify-between">
                <span className="text-[11px] font-mono text-slate-400 truncate max-w-[140px]">
                  {sess.roomName}
                </span>
                <Button
                  size="sm"
                  variant="primary"
                  icon={Video}
                  onClick={() => navigate(`/sessions/${sess.id}/live`)}
                >
                  Join Class
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
