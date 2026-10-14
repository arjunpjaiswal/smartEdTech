import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/client';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { Input } from '../components/common/Input';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorState } from '../components/common/ErrorState';
import { Skeleton } from '../components/common/Skeleton';
import { useToast } from '../components/common/Toast';
import {
  Award,
  Plus,
  Clock,
  Calendar,
  CheckCircle,
  FileText,
  AlertCircle,
  ArrowRight,
  BookOpen,
  Edit3,
  Play,
  RotateCcw,
} from 'lucide-react';

export const AssessmentsPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { addToast } = useToast();

  const [classrooms, setClassrooms] = useState([]);
  const [selectedClassroomId, setSelectedClassroomId] = useState('');
  const [assessments, setAssessments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Create assessment modal
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createTitle, setCreateTitle] = useState('');
  const [createDesc, setCreateDesc] = useState('');
  const [createInstructions, setCreateInstructions] = useState('');
  const [createDuration, setCreateDuration] = useState(60);
  const [createDueDate, setCreateDueDate] = useState('');
  const [createMaxAttempts, setCreateMaxAttempts] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [createError, setCreateError] = useState('');

  // Student attempts map: assessmentId -> latest attempt
  const [studentAttempts, setStudentAttempts] = useState({});

  const fetchClassrooms = useCallback(async () => {
    try {
      const res = await api.get('/classrooms?page=0&size=100');
      const list = res.data?.content || [];
      setClassrooms(list);
      if (list.length > 0 && !selectedClassroomId) {
        setSelectedClassroomId(list[0].id.toString());
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load classrooms.');
    }
  }, [selectedClassroomId]);

  const fetchAssessments = useCallback(async () => {
    if (!selectedClassroomId) {
      setLoading(false);
      return;
    }
    try {
      setLoading(true);
      setError(null);
      const res = await api.get(`/classrooms/${selectedClassroomId}/assessments?page=0&size=50`);
      const items = res.data?.content || [];
      setAssessments(items);

      // If student, also fetch past attempts for these assessments
      if (user?.role === 'STUDENT') {
        const attemptsMap = {};
        for (const ass of items) {
          try {
            const attRes = await api.get(`/assessments/${ass.id}/attempts`);
            if (attRes.data && attRes.data.length > 0) {
              attemptsMap[ass.id] = attRes.data[0]; // Most recent attempt
            }
          } catch (e) {
            console.warn(`Could not fetch student attempts for assessment ${ass.id}:`, e);
          }
        }
        setStudentAttempts(attemptsMap);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load assessments.');
    } finally {
      setLoading(false);
    }
  }, [selectedClassroomId, user]);

  useEffect(() => {
    fetchClassrooms();
  }, [fetchClassrooms]);

  useEffect(() => {
    if (selectedClassroomId) {
      fetchAssessments();
    }
  }, [selectedClassroomId, fetchAssessments]);

  const handleCreateAssessment = async (e) => {
    e.preventDefault();
    if (!selectedClassroomId) return;
    try {
      setSubmitting(true);
      setCreateError('');
      const payload = {
        title: createTitle.trim(),
        description: createDesc.trim() || undefined,
        instructions: createInstructions.trim() || undefined,
        durationMinutes: Number(createDuration),
        dueDate: createDueDate ? new Date(createDueDate).toISOString() : undefined,
        maxAttempts: Number(createMaxAttempts),
      };
      const res = await api.post(`/classrooms/${selectedClassroomId}/assessments`, payload);
      addToast('Assessment created in DRAFT state', 'success');
      setCreateModalOpen(false);
      setCreateTitle('');
      setCreateDesc('');
      setCreateInstructions('');
      setCreateDueDate('');
      // Navigate directly to builder for this assessment
      navigate(`/assessments/${res.data.id}/builder`);
    } catch (err) {
      setCreateError(err.response?.data?.message || 'Failed to create assessment.');
    } finally {
      setSubmitting(false);
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'PUBLISHED':
        return <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800">Published</span>;
      case 'DRAFT':
        return <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800">Draft</span>;
      case 'CLOSED':
        return <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700">Closed</span>;
      case 'ARCHIVED':
        return <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-rose-100 text-rose-800">Archived</span>;
      default:
        return <span className="px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-800">{status}</span>;
    }
  };

  const isTeacher = user?.role === 'TEACHER' || user?.role === 'ADMIN';

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Assessments & Quizzes</h1>
          <p className="text-sm text-slate-500 mt-1">
            {isTeacher
              ? 'Create, generate with AI, review and publish assessments with automated grading.'
              : 'Take quizzes, track scores with AI grading, and access personalized remediation.'}
          </p>
        </div>

        {isTeacher && (
          <Button
            variant="primary"
            onClick={() => setCreateModalOpen(true)}
            disabled={classrooms.length === 0}
          >
            <Plus className="w-4 h-4 mr-2" />
            New Assessment
          </Button>
        )}
      </div>

      {/* Classroom filter */}
      {classrooms.length > 0 && (
        <div className="flex items-center gap-3 bg-white p-3 rounded-xl border border-slate-200">
          <BookOpen className="w-4 h-4 text-slate-400" />
          <span className="text-xs font-medium text-slate-600">Classroom:</span>
          <select
            value={selectedClassroomId}
            onChange={(e) => setSelectedClassroomId(e.target.value)}
            className="text-sm border border-slate-200 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-slate-50"
          >
            {classrooms.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Main Content */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <Skeleton className="h-44 rounded-xl" />
          <Skeleton className="h-44 rounded-xl" />
          <Skeleton className="h-44 rounded-xl" />
        </div>
      ) : error ? (
        <ErrorState message={error} onRetry={fetchAssessments} />
      ) : classrooms.length === 0 ? (
        <EmptyState
          title="No Classrooms Found"
          description={isTeacher ? "Create a classroom first to host assessments." : "Enroll in a classroom to see assessments."}
          icon={BookOpen}
        />
      ) : assessments.length === 0 ? (
        <EmptyState
          title="No Assessments Yet"
          description={
            isTeacher
              ? "You haven't created any assessments in this classroom yet. Click 'New Assessment' to build one."
              : "No assessments are published in this classroom right now."
          }
          icon={Award}
          actionLabel={isTeacher ? "Create Assessment" : undefined}
          onAction={isTeacher ? () => setCreateModalOpen(true) : undefined}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {assessments.map((a) => {
            const latestAttempt = studentAttempts[a.id];
            const isSubmitted = latestAttempt && (latestAttempt.status === 'SUBMITTED' || latestAttempt.status === 'EVALUATED');

            return (
              <Card key={a.id} className="flex flex-col justify-between hover:shadow-md transition-shadow">
                <div className="space-y-3">
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="font-semibold text-slate-900 text-base line-clamp-1">{a.title}</h3>
                    {getStatusBadge(a.status)}
                  </div>

                  {a.description && (
                    <p className="text-xs text-slate-600 line-clamp-2">{a.description}</p>
                  )}

                  <div className="grid grid-cols-2 gap-2 text-xs text-slate-500 pt-2 border-t border-slate-100">
                    <div className="flex items-center gap-1.5">
                      <Clock className="w-3.5 h-3.5 text-slate-400" />
                      <span>{a.durationMinutes} mins</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <Award className="w-3.5 h-3.5 text-slate-400" />
                      <span>{a.totalMarks} Marks</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <FileText className="w-3.5 h-3.5 text-slate-400" />
                      <span>{a.questionCount} Questions</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <RotateCcw className="w-3.5 h-3.5 text-slate-400" />
                      <span>Max {a.maxAttempts} attempt{a.maxAttempts > 1 ? 's' : ''}</span>
                    </div>
                  </div>

                  {a.dueDate && (
                    <div className="flex items-center gap-1.5 text-[11px] text-amber-700 bg-amber-50 px-2 py-1 rounded">
                      <Calendar className="w-3 h-3 text-amber-600" />
                      <span>Due: {new Date(a.dueDate).toLocaleDateString()} {new Date(a.dueDate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                    </div>
                  )}

                  {/* Student past score badge */}
                  {!isTeacher && latestAttempt && (
                    <div className="p-2 bg-slate-50 border border-slate-200 rounded-lg text-xs flex justify-between items-center">
                      <span className="text-slate-600 font-medium">Attempt #{latestAttempt.attemptNumber}:</span>
                      <span className="font-bold text-slate-900">
                        {latestAttempt.totalScore !== null ? `${latestAttempt.totalScore} / ${latestAttempt.maxScore}` : latestAttempt.status}
                      </span>
                    </div>
                  )}
                </div>

                <div className="pt-4 border-t border-slate-100 mt-4 flex items-center justify-end gap-2">
                  {isTeacher ? (
                    <>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => navigate(`/assessments/${a.id}/builder`)}
                      >
                        <Edit3 className="w-3.5 h-3.5 mr-1" />
                        {a.status === 'DRAFT' ? 'Edit / Build' : 'View Questions'}
                      </Button>
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => navigate(`/assessments/${a.id}/attempts`)}
                      >
                        Results
                      </Button>
                    </>
                  ) : (
                    <>
                      {isSubmitted ? (
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => navigate(`/attempts/${latestAttempt.id}/results`)}
                        >
                          View Results & Remediation
                        </Button>
                      ) : (
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => navigate(`/assessments/${a.id}/take`)}
                        >
                          <Play className="w-3.5 h-3.5 mr-1" />
                          Start Assessment
                        </Button>
                      )}
                    </>
                  )}
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* Create Assessment Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        title="Create New Assessment"
      >
        <form onSubmit={handleCreateAssessment} className="space-y-4">
          {createError && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-xs text-red-800">
              {createError}
            </div>
          )}

          <Input
            label="Assessment Title"
            value={createTitle}
            onChange={(e) => setCreateTitle(e.target.value)}
            placeholder="e.g. Unit 3 Kinematics Mid-term"
            required
          />

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Description</label>
            <textarea
              value={createDesc}
              onChange={(e) => setCreateDesc(e.target.value)}
              placeholder="Brief summary of assessment topics covered"
              rows={2}
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Instructions for Students</label>
            <textarea
              value={createInstructions}
              onChange={(e) => setCreateInstructions(e.target.value)}
              placeholder="e.g. Answer all questions. Scientific calculator permitted."
              rows={2}
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <Input
                label="Duration (mins)"
                type="number"
                min="5"
                max="300"
                value={createDuration}
                onChange={(e) => setCreateDuration(e.target.value)}
                required
              />
            </div>
            <div>
              <Input
                label="Max Attempts"
                type="number"
                min="1"
                max="10"
                value={createMaxAttempts}
                onChange={(e) => setCreateMaxAttempts(e.target.value)}
                required
              />
            </div>
            <div>
              <Input
                label="Due Date (Optional)"
                type="datetime-local"
                value={createDueDate}
                onChange={(e) => setCreateDueDate(e.target.value)}
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-slate-200">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setCreateModalOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={submitting}>
              {submitting ? 'Creating...' : 'Continue to Question Builder'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
