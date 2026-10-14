import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/client';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { Input } from '../components/common/Input';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorState } from '../components/common/ErrorState';
import { Skeleton } from '../components/common/Skeleton';
import { useToast } from '../components/common/Toast';
import {
  BookOpen,
  Plus,
  Users,
  Copy,
  Check,
  Archive,
  ArrowRight,
  GraduationCap,
} from 'lucide-react';

export const ClassroomsPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { addToast } = useToast();

  const [classrooms, setClassrooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Create Classroom Modal (Teacher/Admin)
  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [submittingCreate, setSubmittingCreate] = useState(false);
  const [formError, setFormError] = useState('');

  // Join Classroom Modal (Student)
  const [joinOpen, setJoinOpen] = useState(false);
  const [joinCode, setJoinCode] = useState('');
  const [submittingJoin, setSubmittingJoin] = useState(false);
  const [joinError, setJoinError] = useState('');

  // Copied code feedback
  const [copiedCode, setCopiedCode] = useState(null);

  const fetchClassrooms = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.get('/classrooms?size=50');
      setClassrooms(response.data.content || []);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load classrooms.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchClassrooms();
  }, [fetchClassrooms]);

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    if (!name.trim()) {
      setFormError('Classroom name is required');
      return;
    }
    setSubmittingCreate(true);
    setFormError('');
    try {
      await api.post('/classrooms', { name: name.trim(), description: description.trim() });
      addToast('Classroom created successfully!', 'success');
      setName('');
      setDescription('');
      setCreateOpen(false);
      fetchClassrooms();
    } catch (err) {
      setFormError(err.response?.data?.message || 'Failed to create classroom');
    } finally {
      setSubmittingCreate(false);
    }
  };

  const handleJoinSubmit = async (e) => {
    e.preventDefault();
    if (!joinCode.trim()) {
      setJoinError('Join code is required');
      return;
    }
    setSubmittingJoin(true);
    setJoinError('');
    try {
      await api.post('/classrooms/join', { joinCode: joinCode.trim() });
      addToast('Successfully enrolled in classroom!', 'success');
      setJoinCode('');
      setJoinOpen(false);
      fetchClassrooms();
    } catch (err) {
      setJoinError(err.response?.data?.message || 'Failed to join classroom');
    } finally {
      setSubmittingJoin(false);
    }
  };

  const handleCopyCode = (code, e) => {
    e.stopPropagation();
    navigator.clipboard.writeText(code);
    setCopiedCode(code);
    addToast(`Join code ${code} copied to clipboard!`, 'info');
    setTimeout(() => setCopiedCode(null), 2500);
  };

  const handleToggleArchive = async (cls, e) => {
    e.stopPropagation();
    try {
      await api.patch(`/classrooms/${cls.id}/archive`, { archived: !cls.archived });
      addToast(cls.archived ? 'Classroom restored.' : 'Classroom archived.', 'success');
      fetchClassrooms();
    } catch (err) {
      addToast(err.response?.data?.message || 'Failed to update archive status', 'error');
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Classrooms</h1>
          <p className="text-sm text-slate-500 mt-1">
            {user?.role === 'STUDENT'
              ? 'Classes and subjects you are currently attending'
              : 'Spaces for courses, live sessions, materials, and quizzes'}
          </p>
        </div>

        <div className="flex items-center gap-3">
          {(user?.role === 'TEACHER' || user?.role === 'ADMIN') && (
            <Button variant="primary" icon={Plus} onClick={() => setCreateOpen(true)}>
              Create Classroom
            </Button>
          )}
          {user?.role === 'STUDENT' && (
            <Button variant="primary" icon={GraduationCap} onClick={() => setJoinOpen(true)}>
              Join with Code
            </Button>
          )}
        </div>
      </div>

      {/* Main Content */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <Skeleton className="h-44 w-full" />
          <Skeleton className="h-44 w-full" />
          <Skeleton className="h-44 w-full" />
        </div>
      ) : error ? (
        <ErrorState title="Error Loading Classrooms" message={error} onRetry={fetchClassrooms} />
      ) : classrooms.length === 0 ? (
        <EmptyState
          icon={BookOpen}
          title={user?.role === 'STUDENT' ? 'No Enrolled Classrooms' : 'No Classrooms Created'}
          description={
            user?.role === 'STUDENT'
              ? 'You have not joined any classrooms yet. Ask your instructor for an 8-character invite code.'
              : 'Create your first classroom to begin inviting students, uploading materials, and hosting live sessions.'
          }
          action={
            user?.role === 'STUDENT' ? (
              <Button variant="primary" icon={GraduationCap} onClick={() => setJoinOpen(true)}>
                Join with Code
              </Button>
            ) : (
              <Button variant="primary" icon={Plus} onClick={() => setCreateOpen(true)}>
                Create Classroom
              </Button>
            )
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {classrooms.map((cls) => (
            <div
              key={cls.id}
              onClick={() => navigate(`/classrooms/${cls.id}`)}
              className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm hover:shadow-md transition-all cursor-pointer flex flex-col justify-between group relative"
            >
              <div>
                <div className="flex items-start justify-between gap-2">
                  <div className="w-10 h-10 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center font-bold">
                    <BookOpen className="w-5 h-5" />
                  </div>
                  {cls.archived && (
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-50 text-amber-700 border border-amber-200">
                      Archived
                    </span>
                  )}
                </div>

                <h3 className="text-base font-bold text-slate-900 mt-3 group-hover:text-indigo-600 transition-colors">
                  {cls.name}
                </h3>
                <p className="text-xs text-slate-500 mt-1 line-clamp-2">
                  {cls.description || 'No description provided.'}
                </p>
              </div>

              <div className="mt-5 pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
                <div className="flex items-center gap-1.5">
                  <Users className="w-3.5 h-3.5 text-slate-400" />
                  <span>{cls.studentCount} students</span>
                </div>

                {(user?.role === 'TEACHER' || user?.role === 'ADMIN') ? (
                  <div className="flex items-center gap-2">
                    <button
                      onClick={(e) => handleCopyCode(cls.joinCode, e)}
                      title="Copy Join Code"
                      className="px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 font-mono font-bold text-[11px] flex items-center gap-1"
                    >
                      {copiedCode === cls.joinCode ? <Check className="w-3 h-3 text-emerald-600" /> : <Copy className="w-3 h-3" />}
                      {cls.joinCode}
                    </button>
                    <button
                      onClick={(e) => handleToggleArchive(cls, e)}
                      title={cls.archived ? 'Restore' : 'Archive'}
                      className="p-1 text-slate-400 hover:text-slate-600"
                    >
                      <Archive className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ) : (
                  <span className="text-indigo-600 font-medium flex items-center gap-1">
                    Enter <ArrowRight className="w-3 h-3" />
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      <Modal isOpen={createOpen} onClose={() => setCreateOpen(false)} title="Create New Classroom">
        <form onSubmit={handleCreateSubmit} className="space-y-4">
          {formError && (
            <div className="p-3 rounded-lg bg-rose-50 text-rose-700 text-xs border border-rose-200">
              {formError}
            </div>
          )}
          <Input
            label="Classroom Name"
            placeholder="e.g. Advanced Calculus"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Description (Optional)
            </label>
            <textarea
              rows={3}
              className="w-full px-3.5 py-2 text-sm rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-colors"
              placeholder="What topics are covered in this classroom?"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" loading={submittingCreate}>
              Create Classroom
            </Button>
          </div>
        </form>
      </Modal>

      {/* Join Modal */}
      <Modal isOpen={joinOpen} onClose={() => setJoinOpen(false)} title="Join Classroom">
        <form onSubmit={handleJoinSubmit} className="space-y-4">
          {joinError && (
            <div className="p-3 rounded-lg bg-rose-50 text-rose-700 text-xs border border-rose-200">
              {joinError}
            </div>
          )}
          <p className="text-xs text-slate-500">
            Enter the 8-character code given to you by your teacher to join the classroom.
          </p>
          <Input
            label="Classroom Join Code"
            placeholder="e.g. ABC123XY"
            value={joinCode}
            onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
            required
            autoFocus
          />
          <div className="flex justify-end gap-3 pt-2">
            <Button variant="secondary" onClick={() => setJoinOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" loading={submittingJoin}>
              Join Classroom
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
