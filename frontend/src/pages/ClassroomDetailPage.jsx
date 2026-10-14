import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
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
  ArrowLeft,
  Calendar,
  FileText,
  Users,
  Plus,
  Video,
  Download,
  Trash2,
  Copy,
  Check,
  Clock,
  UserPlus,
  UserMinus,
  FileUp,
} from 'lucide-react';

export const ClassroomDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { addToast } = useToast();

  const [activeTab, setActiveTab] = useState('sessions'); // 'sessions' | 'materials' | 'students'
  const [classroom, setClassroom] = useState(null);
  const [loadingClassroom, setLoadingClassroom] = useState(true);
  const [classroomError, setClassroomError] = useState(null);

  // Sessions state
  const [sessions, setSessions] = useState([]);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const [sessionModalOpen, setSessionModalOpen] = useState(false);
  const [sessionTitle, setSessionTitle] = useState('');
  const [sessionDesc, setSessionDesc] = useState('');
  const [startTime, setStartTime] = useState('');
  const [endTime, setEndTime] = useState('');
  const [submittingSession, setSubmittingSession] = useState(false);
  const [sessionError, setSessionError] = useState('');

  // Materials state
  const [materials, setMaterials] = useState([]);
  const [loadingMaterials, setLoadingMaterials] = useState(false);
  const [materialModalOpen, setMaterialModalOpen] = useState(false);
  const [materialTitle, setMaterialTitle] = useState('');
  const [materialDesc, setMaterialDesc] = useState('');
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadingMaterial, setUploadingMaterial] = useState(false);
  const [materialError, setMaterialError] = useState('');
  const fileInputRef = useRef(null);

  // Students state
  const [students, setStudents] = useState([]);
  const [loadingStudents, setLoadingStudents] = useState(false);
  const [enrollModalOpen, setEnrollModalOpen] = useState(false);
  const [enrollEmail, setEnrollEmail] = useState('');
  const [submittingEnroll, setSubmittingEnroll] = useState(false);
  const [enrollError, setEnrollError] = useState('');
  const [studentsError, setStudentsError] = useState('');

  const [copiedCode, setCopiedCode] = useState(false);

  const isTeacherOrAdmin = user?.role === 'TEACHER' || user?.role === 'ADMIN';

  const fetchClassroom = useCallback(async () => {
    setLoadingClassroom(true);
    setClassroomError(null);
    try {
      const res = await api.get(`/classrooms/${id}`);
      setClassroom(res.data);
    } catch (err) {
      setClassroomError(err.response?.data?.message || 'Failed to load classroom details');
    } finally {
      setLoadingClassroom(false);
    }
  }, [id]);

  const fetchSessions = useCallback(async () => {
    setLoadingSessions(true);
    setSessionError('');
    try {
      const res = await api.get(`/classrooms/${id}/sessions?size=50`);
      setSessions(res.data.content || []);
    } catch (err) {
      setSessionError(err.response?.data?.message || 'Failed to load classroom sessions');
    } finally {
      setLoadingSessions(false);
    }
  }, [id]);

  const fetchMaterials = useCallback(async () => {
    setLoadingMaterials(true);
    setMaterialError('');
    try {
      const res = await api.get(`/classrooms/${id}/materials?size=50`);
      setMaterials(res.data.content || []);
    } catch (err) {
      setMaterialError(err.response?.data?.message || 'Failed to load classroom materials');
    } finally {
      setLoadingMaterials(false);
    }
  }, [id]);

  const fetchStudents = useCallback(async () => {
    setLoadingStudents(true);
    setStudentsError('');
    try {
      const res = await api.get(`/classrooms/${id}/students?size=50`);
      setStudents(res.data.content || []);
    } catch (err) {
      setStudentsError(err.response?.data?.message || 'Failed to load enrolled students');
    } finally {
      setLoadingStudents(false);
    }
  }, [id]);

  useEffect(() => {
    fetchClassroom();
  }, [fetchClassroom]);

  useEffect(() => {
    if (activeTab === 'sessions') fetchSessions();
    if (activeTab === 'materials') fetchMaterials();
    if (activeTab === 'students') fetchStudents();
  }, [activeTab, fetchSessions, fetchMaterials, fetchStudents]);

  const handleCopyCode = () => {
    if (!classroom) return;
    navigator.clipboard.writeText(classroom.joinCode);
    setCopiedCode(true);
    addToast(`Join code ${classroom.joinCode} copied!`, 'info');
    setTimeout(() => setCopiedCode(false), 2500);
  };

  // Schedule Session
  const handleScheduleSession = async (e) => {
    e.preventDefault();
    if (!sessionTitle.trim() || !startTime || !endTime) {
      setSessionError('Title, start time, and end time are required');
      return;
    }
    setSubmittingSession(true);
    setSessionError('');
    try {
      await api.post(`/classrooms/${id}/sessions`, {
        title: sessionTitle.trim(),
        description: sessionDesc.trim() || null,
        scheduledStartTime: new Date(startTime).toISOString(),
        scheduledEndTime: new Date(endTime).toISOString(),
      });
      addToast('Live session scheduled!', 'success');
      setSessionTitle('');
      setSessionDesc('');
      setStartTime('');
      setEndTime('');
      setSessionModalOpen(false);
      fetchSessions();
    } catch (err) {
      setSessionError(err.response?.data?.message || 'Failed to schedule session');
    } finally {
      setSubmittingSession(false);
    }
  };

  // Upload Material
  const handleUploadMaterial = async (e) => {
    e.preventDefault();
    if (!selectedFile) {
      setMaterialError('Please select a file to upload');
      return;
    }
    setUploadingMaterial(true);
    setMaterialError('');

    const formData = new FormData();
    formData.append('file', selectedFile);
    if (materialTitle.trim()) formData.append('title', materialTitle.trim());
    if (materialDesc.trim()) formData.append('description', materialDesc.trim());

    try {
      await api.post(`/classrooms/${id}/materials`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      addToast('Study material uploaded!', 'success');
      setMaterialTitle('');
      setMaterialDesc('');
      setSelectedFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      setMaterialModalOpen(false);
      fetchMaterials();
    } catch (err) {
      setMaterialError(err.response?.data?.message || 'Failed to upload material');
    } finally {
      setUploadingMaterial(false);
    }
  };

  // Download Material
  const handleDownloadMaterial = async (mat) => {
    try {
      const response = await api.get(`/materials/${mat.id}/download`, { responseType: 'blob' });
      const blob = new Blob([response.data], { type: mat.contentType });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', mat.originalFilename);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      addToast(err.response?.data?.message || 'Download failed', 'error');
    }
  };

  // Delete Material
  const handleDeleteMaterial = async (matId) => {
    if (!window.confirm('Are you sure you want to delete this study material?')) return;
    try {
      await api.delete(`/materials/${matId}`);
      addToast('Material deleted.', 'success');
      fetchMaterials();
    } catch (err) {
      addToast(err.response?.data?.message || 'Failed to delete material', 'error');
    }
  };

  // Enroll Student
  const handleEnrollStudent = async (e) => {
    e.preventDefault();
    if (!enrollEmail.trim()) {
      setEnrollError('Student email is required');
      return;
    }
    setSubmittingEnroll(true);
    setEnrollError('');
    try {
      await api.post(`/classrooms/${id}/students`, { email: enrollEmail.trim() });
      addToast('Student enrolled successfully!', 'success');
      setEnrollEmail('');
      setEnrollModalOpen(false);
      fetchStudents();
    } catch (err) {
      setEnrollError(err.response?.data?.message || 'Failed to enroll student');
    } finally {
      setSubmittingEnroll(false);
    }
  };

  // Remove Student
  const handleRemoveStudent = async (studentId) => {
    if (!window.confirm('Are you sure you want to remove this student from the classroom?')) return;
    try {
      await api.delete(`/classrooms/${id}/students/${studentId}`);
      addToast('Student removed from classroom.', 'success');
      fetchStudents();
    } catch (err) {
      addToast(err.response?.data?.message || 'Failed to remove student', 'error');
    }
  };

  if (loadingClassroom) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-44 w-full" />
      </div>
    );
  }

  if (classroomError) {
    return (
      <ErrorState
        title="Classroom Not Found"
        message={classroomError}
        onRetry={fetchClassroom}
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* Back button & Title Header */}
      <div>
        <button
          onClick={() => navigate('/classrooms')}
          className="text-xs font-semibold text-slate-500 hover:text-slate-800 flex items-center gap-1 mb-3 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Classrooms
        </button>

        <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-sm flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2.5">
              <h1 className="text-2xl font-bold text-slate-900">{classroom?.name}</h1>
              {classroom?.archived && (
                <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-amber-50 text-amber-700 border border-amber-200">
                  Archived
                </span>
              )}
            </div>
            <p className="text-sm text-slate-500 mt-1 max-w-2xl">{classroom?.description || 'No description provided.'}</p>
            <p className="text-xs text-slate-400 mt-2">
              Instructor: <span className="font-semibold text-slate-700">{classroom?.teacherName}</span>
            </p>
          </div>

          {/* Join Code Display */}
          {isTeacherOrAdmin && (
            <div className="bg-slate-50 border border-slate-200 rounded-xl p-3 flex items-center gap-3">
              <div>
                <span className="text-[10px] uppercase font-bold text-slate-400 block tracking-wider">Classroom Code</span>
                <span className="text-base font-mono font-bold text-indigo-700">{classroom?.joinCode}</span>
              </div>
              <button
                onClick={handleCopyCode}
                title="Copy code"
                className="p-2 rounded-lg bg-white border border-slate-200 hover:bg-slate-100 text-slate-600 transition-colors"
              >
                {copiedCode ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-200 gap-8">
        <button
          onClick={() => setActiveTab('sessions')}
          className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition-colors ${
            activeTab === 'sessions'
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Calendar className="w-4 h-4" /> Live Sessions
        </button>

        <button
          onClick={() => setActiveTab('materials')}
          className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition-colors ${
            activeTab === 'materials'
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <FileText className="w-4 h-4" /> Study Materials
        </button>

        <button
          onClick={() => setActiveTab('students')}
          className={`pb-3 text-sm font-semibold flex items-center gap-2 border-b-2 transition-colors ${
            activeTab === 'students'
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Users className="w-4 h-4" /> Enrolled Students ({classroom?.studentCount || 0})
        </button>
      </div>

      {/* Tab 1: Live Sessions */}
      {activeTab === 'sessions' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-slate-900">Scheduled Sessions</h2>
            {isTeacherOrAdmin && (
              <Button variant="primary" size="sm" icon={Plus} onClick={() => setSessionModalOpen(true)}>
                Schedule Live Session
              </Button>
            )}
          </div>

          {sessionError ? (
            <ErrorState message={sessionError} onRetry={fetchSessions} />
          ) : loadingSessions ? (
            <div className="space-y-3">
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
            </div>
          ) : sessions.length === 0 ? (
            <EmptyState
              icon={Calendar}
              title="No Live Sessions Scheduled"
              description="There are currently no scheduled video lectures or classes for this classroom."
              action={
                isTeacherOrAdmin && (
                  <Button variant="primary" size="sm" icon={Plus} onClick={() => setSessionModalOpen(true)}>
                    Schedule Session
                  </Button>
                )
              }
            />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {sessions.map((sess) => (
                <div key={sess.id} className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm flex flex-col justify-between">
                  <div>
                    <div className="flex items-center justify-between">
                      <h3 className="text-base font-bold text-slate-900">{sess.title}</h3>
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                          sess.status === 'ACTIVE'
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                            : sess.status === 'CANCELLED'
                            ? 'bg-rose-50 text-rose-700 border border-rose-200'
                            : 'bg-indigo-50 text-indigo-700 border border-indigo-200'
                        }`}
                      >
                        {sess.status}
                      </span>
                    </div>
                    <p className="text-xs text-slate-500 mt-1 line-clamp-2">{sess.description || 'No description'}</p>
                    <div className="flex items-center gap-1.5 text-xs text-slate-400 mt-3">
                      <Clock className="w-3.5 h-3.5" />
                      <span>{new Date(sess.scheduledStartTime).toLocaleString()}</span>
                    </div>
                  </div>

                  <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between">
                    <span className="text-[11px] text-slate-400 font-mono">Room: {sess.roomName}</span>
                    {sess.status !== 'CANCELLED' && (
                      <Button
                        size="sm"
                        variant="primary"
                        icon={Video}
                        onClick={() => navigate(`/sessions/${sess.id}/live`)}
                      >
                        Join Room
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Study Materials */}
      {activeTab === 'materials' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-slate-900">Study Materials</h2>
            {isTeacherOrAdmin && (
              <Button variant="primary" size="sm" icon={FileUp} onClick={() => setMaterialModalOpen(true)}>
                Upload Material
              </Button>
            )}
          </div>

          {materialError ? (
            <ErrorState message={materialError} onRetry={fetchMaterials} />
          ) : loadingMaterials ? (
            <div className="space-y-3">
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
            </div>
          ) : materials.length === 0 ? (
            <EmptyState
              icon={FileText}
              title="No Study Materials Yet"
              description="No PDF documents or lecture slides have been uploaded to this classroom."
              action={
                isTeacherOrAdmin && (
                  <Button variant="primary" size="sm" icon={FileUp} onClick={() => setMaterialModalOpen(true)}>
                    Upload Material
                  </Button>
                )
              }
            />
          ) : (
            <div className="bg-white rounded-2xl border border-slate-200 divide-y divide-slate-100 overflow-hidden shadow-sm">
              {materials.map((mat) => (
                <div key={mat.id} className="p-4 flex items-center justify-between hover:bg-slate-50 transition-colors">
                  <div className="flex items-start gap-3">
                    <div className="w-9 h-9 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center font-bold">
                      <FileText className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-slate-900">{mat.title}</h4>
                      <p className="text-xs text-slate-500 line-clamp-1">{mat.description || mat.originalFilename}</p>
                      <div className="flex items-center gap-3 text-[11px] text-slate-400 mt-1">
                        <span>{(mat.fileSize / 1024 / 1024).toFixed(2)} MB</span>
                        <span>•</span>
                        <span>{new Date(mat.createdAt).toLocaleDateString()}</span>
                        <span>•</span>
                        <span>By {mat.uploaderName}</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <Button
                      size="sm"
                      variant="secondary"
                      icon={Download}
                      onClick={() => handleDownloadMaterial(mat)}
                    >
                      Download
                    </Button>
                    {isTeacherOrAdmin && (
                      <button
                        onClick={() => handleDeleteMaterial(mat.id)}
                        className="p-2 text-slate-400 hover:text-rose-600 transition-colors"
                        title="Delete material"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab 3: Enrolled Students */}
      {activeTab === 'students' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-slate-900">Enrolled Students</h2>
            {isTeacherOrAdmin && (
              <Button variant="primary" size="sm" icon={UserPlus} onClick={() => setEnrollModalOpen(true)}>
                Enroll Student
              </Button>
            )}
          </div>

          {studentsError ? (
            <ErrorState message={studentsError} onRetry={fetchStudents} />
          ) : loadingStudents ? (
            <div className="space-y-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : students.length === 0 ? (
            <EmptyState
              icon={Users}
              title="No Students Enrolled"
              description="Share the classroom join code or invite students directly by email."
              action={
                isTeacherOrAdmin && (
                  <Button variant="primary" size="sm" icon={UserPlus} onClick={() => setEnrollModalOpen(true)}>
                    Enroll Student
                  </Button>
                )
              }
            />
          ) : (
            <div className="bg-white rounded-2xl border border-slate-200 divide-y divide-slate-100 overflow-hidden shadow-sm">
              {students.map((st) => (
                <div key={st.id} className="p-4 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-full bg-slate-100 text-slate-700 flex items-center justify-center font-bold text-xs">
                      {st.studentName.charAt(0).toUpperCase()}
                    </div>
                    <div>
                      <h4 className="text-sm font-semibold text-slate-900">{st.studentName}</h4>
                      <p className="text-xs text-slate-500">{st.studentEmail}</p>
                    </div>
                  </div>

                  <div className="flex items-center gap-4">
                    <span className="text-xs text-slate-400">
                      Joined {new Date(st.enrolledAt).toLocaleDateString()}
                    </span>
                    {isTeacherOrAdmin && (
                      <button
                        onClick={() => handleRemoveStudent(st.studentId)}
                        className="text-slate-400 hover:text-rose-600 p-1"
                        title="Remove student"
                      >
                        <UserMinus className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Schedule Session Modal */}
      <Modal isOpen={sessionModalOpen} onClose={() => setSessionModalOpen(false)} title="Schedule Live Session">
        <form onSubmit={handleScheduleSession} className="space-y-4">
          {sessionError && (
            <div className="p-3 rounded-lg bg-rose-50 text-rose-700 text-xs border border-rose-200">
              {sessionError}
            </div>
          )}
          <Input
            label="Session Title"
            placeholder="e.g. Lecture 4: Tree Traversals"
            value={sessionTitle}
            onChange={(e) => setSessionTitle(e.target.value)}
            required
          />
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Description (Optional)
            </label>
            <textarea
              rows={2}
              className="w-full px-3.5 py-2 text-sm rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="Session objectives and agenda"
              value={sessionDesc}
              onChange={(e) => setSessionDesc(e.target.value)}
            />
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              label="Start Time"
              type="datetime-local"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
              required
            />
            <Input
              label="End Time"
              type="datetime-local"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
              required
            />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <Button variant="secondary" onClick={() => setSessionModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" loading={submittingSession}>
              Schedule Session
            </Button>
          </div>
        </form>
      </Modal>

      {/* Upload Material Modal */}
      <Modal isOpen={materialModalOpen} onClose={() => setMaterialModalOpen(false)} title="Upload Study Material">
        <form onSubmit={handleUploadMaterial} className="space-y-4">
          {materialError && (
            <div className="p-3 rounded-lg bg-rose-50 text-rose-700 text-xs border border-rose-200">
              {materialError}
            </div>
          )}
          <Input
            label="Title"
            placeholder="e.g. Chapter 3 Lecture Slides"
            value={materialTitle}
            onChange={(e) => setMaterialTitle(e.target.value)}
          />
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              File (PDF, DOC, DOCX, PPT, TXT, max 20MB)
            </label>
            <input
              type="file"
              ref={fileInputRef}
              accept=".pdf,.doc,.docx,.ppt,.pptx,.txt"
              onChange={(e) => setSelectedFile(e.target.files[0] || null)}
              className="block w-full text-sm text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100"
              required
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1.5">
              Description (Optional)
            </label>
            <textarea
              rows={2}
              className="w-full px-3.5 py-2 text-sm rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="Material notes or instructions"
              value={materialDesc}
              onChange={(e) => setMaterialDesc(e.target.value)}
            />
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <Button variant="secondary" onClick={() => setMaterialModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" loading={uploadingMaterial}>
              Upload File
            </Button>
          </div>
        </form>
      </Modal>

      {/* Enroll Student Modal */}
      <Modal isOpen={enrollModalOpen} onClose={() => setEnrollModalOpen(false)} title="Enroll Student">
        <form onSubmit={handleEnrollStudent} className="space-y-4">
          {enrollError && (
            <div className="p-3 rounded-lg bg-rose-50 text-rose-700 text-xs border border-rose-200">
              {enrollError}
            </div>
          )}
          <p className="text-xs text-slate-500">
            Enter the student's registered email address to add them to this classroom.
          </p>
          <Input
            label="Student Email"
            type="email"
            placeholder="student@example.com"
            value={enrollEmail}
            onChange={(e) => setEnrollEmail(e.target.value)}
            required
            autoFocus
          />
          <div className="flex justify-end gap-3 pt-2">
            <Button variant="secondary" onClick={() => setEnrollModalOpen(false)}>
              Cancel
            </Button>
            <Button variant="primary" type="submit" loading={submittingEnroll}>
              Enroll Student
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
