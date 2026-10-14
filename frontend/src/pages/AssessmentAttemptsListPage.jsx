import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Table } from '../components/common/Table';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorState } from '../components/common/ErrorState';
import { Skeleton } from '../components/common/Skeleton';
import { ArrowLeft, Users, Award, Clock, ArrowRight } from 'lucide-react';

export const AssessmentAttemptsListPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();

  const [assessment, setAssessment] = useState(null);
  const [attempts, setAttempts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [assRes, attRes] = await Promise.all([
        api.get(`/assessments/${id}`),
        api.get(`/assessments/${id}/attempts`),
      ]);
      setAssessment(assRes.data);
      setAttempts(attRes.data || []);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load assessment submissions.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-24 rounded-xl" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  if (error || !assessment) {
    return <ErrorState message={error || 'Assessment not found.'} onRetry={fetchData} />;
  }

  const columns = [
    {
      header: 'Student',
      render: (a) => (
        <div>
          <div className="font-semibold text-slate-900">{a.studentName}</div>
          <div className="text-xs text-slate-500">Attempt #{a.attemptNumber}</div>
        </div>
      ),
    },
    {
      header: 'Score',
      render: (a) => (
        <span className="font-bold text-slate-900">
          {a.totalScore !== null ? `${a.totalScore} / ${a.maxScore}` : '--'}
        </span>
      ),
    },
    {
      header: 'Status',
      render: (a) => (
        <span
          className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
            a.status === 'EVALUATED'
              ? 'bg-emerald-100 text-emerald-800'
              : a.status === 'SUBMITTED'
              ? 'bg-amber-100 text-amber-800'
              : 'bg-slate-100 text-slate-800'
          }`}
        >
          {a.status === 'SUBMITTED' ? 'Provisional' : a.status}
        </span>
      ),
    },
    {
      header: 'Submitted At',
      render: (a) => (
        <span className="text-xs text-slate-600">
          {a.submittedAt ? new Date(a.submittedAt).toLocaleString() : 'In Progress'}
        </span>
      ),
    },
    {
      header: 'Action',
      render: (a) => (
        <Button
          variant="secondary"
          size="sm"
          onClick={() => navigate(`/attempts/${a.id}/results`)}
        >
          Review & Grade <ArrowRight className="w-3.5 h-3.5 ml-1" />
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <Button variant="secondary" size="sm" onClick={() => navigate('/assessments')}>
          <ArrowLeft className="w-4 h-4 mr-1.5" /> Back to Assessments
        </Button>
      </div>

      <Card className="p-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <span className="text-xs uppercase tracking-wider text-indigo-600 font-bold">
              Submissions & Grading
            </span>
            <h1 className="text-2xl font-bold text-slate-900 mt-1">{assessment.title}</h1>
            <p className="text-sm text-slate-500 mt-1">Classroom: {assessment.classroomTitle}</p>
          </div>

          <div className="flex items-center gap-3">
            <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl text-center">
              <div className="text-xl font-bold text-slate-900">{attempts.length}</div>
              <div className="text-[11px] text-slate-500 font-medium">Total Submissions</div>
            </div>
            <Button
              variant="secondary"
              onClick={() => navigate(`/assessments/${id}/builder`)}
            >
              View Assessment Questions
            </Button>
          </div>
        </div>
      </Card>

      {attempts.length === 0 ? (
        <EmptyState
          title="No Submissions Yet"
          description="Enrolled students have not submitted attempts for this assessment yet."
          icon={Users}
        />
      ) : (
        <Card className="p-0 overflow-hidden">
          <Table columns={columns} data={attempts} />
        </Card>
      )}
    </div>
  );
};
