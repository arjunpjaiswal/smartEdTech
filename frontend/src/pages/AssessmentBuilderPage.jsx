import React, { useState, useEffect, useCallback } from 'react';
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
import { AiGeneratorModal } from '../components/assessment/AiGeneratorModal';
import {
  ArrowLeft,
  Sparkles,
  Plus,
  Trash2,
  ChevronUp,
  ChevronDown,
  CheckCircle,
  HelpCircle,
  FileText,
  Clock,
  Award,
  Send,
  AlertTriangle,
  Archive,
} from 'lucide-react';

export const AssessmentBuilderPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { addToast } = useToast();

  const [assessment, setAssessment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // AI Generator modal
  const [aiModalOpen, setAiModalOpen] = useState(false);

  // Manual Question modal
  const [manualModalOpen, setManualModalOpen] = useState(false);
  const [qText, setQText] = useState('');
  const [qType, setQType] = useState('MCQ');
  const [qMarks, setQMarks] = useState(1);
  const [qModelAnswer, setQModelAnswer] = useState('');
  const [qRubric, setQRubric] = useState('');
  const [mcqOptions, setMcqOptions] = useState([
    { text: '', isCorrect: true },
    { text: '', isCorrect: false },
    { text: '', isCorrect: false },
    { text: '', isCorrect: false },
  ]);
  const [savingQuestion, setSavingQuestion] = useState(false);
  const [questionError, setQuestionError] = useState('');

  // Publishing
  const [publishing, setPublishing] = useState(false);

  const fetchAssessment = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get(`/assessments/${id}`);
      setAssessment(res.data);
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load assessment.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchAssessment();
  }, [fetchAssessment]);

  const handlePublish = async () => {
    if (!assessment) return;
    if (assessment.questions.length === 0) {
      addToast('Cannot publish assessment: please add at least one question.', 'error');
      return;
    }
    try {
      setPublishing(true);
      const res = await api.put(`/assessments/${id}/publish`);
      setAssessment(res.data);
      addToast('Assessment published successfully! Enrolled students have been notified.', 'success');
    } catch (err) {
      addToast(err.response?.data?.message || 'Failed to publish assessment.', 'error');
    } finally {
      setPublishing(false);
    }
  };

  const handleClose = async () => {
    try {
      const res = await api.put(`/assessments/${id}/close`);
      setAssessment(res.data);
      addToast('Assessment closed for student submissions.', 'info');
    } catch (err) {
      addToast(err.response?.data?.message || 'Failed to close assessment.', 'error');
    }
  };

  const handleDeleteQuestion = async (qId) => {
    if (!window.confirm('Are you sure you want to delete this question?')) return;
    try {
      await api.delete(`/assessments/${id}/questions/${qId}`);
      addToast('Question deleted', 'success');
      fetchAssessment();
    } catch (err) {
      addToast(err.response?.data?.message || 'Failed to delete question.', 'error');
    }
  };

  const handleMoveQuestion = async (index, direction) => {
    if (!assessment || !assessment.questions) return;
    const questions = [...assessment.questions];
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= questions.length) return;

    // Swap
    const temp = questions[index];
    questions[index] = questions[targetIndex];
    questions[targetIndex] = temp;

    const orderedIds = questions.map((q) => q.id);
    try {
      await api.put(`/assessments/${id}/questions/reorder`, { questionIds: orderedIds });
      fetchAssessment();
    } catch (err) {
      addToast('Failed to reorder questions.', 'error');
    }
  };

  const handleSaveManualQuestion = async (e) => {
    e.preventDefault();
    setQuestionError('');

    if (qType === 'MCQ') {
      const validOptions = mcqOptions.filter((o) => o.text.trim().length > 0);
      if (validOptions.length < 2) {
        setQuestionError('MCQ requires at least 2 non-empty options.');
        return;
      }
      const correctCount = validOptions.filter((o) => o.isCorrect).length;
      if (correctCount !== 1) {
        setQuestionError('Please select exactly one correct option.');
        return;
      }
    } else {
      if (!qModelAnswer.trim()) {
        setQuestionError('Subjective and one-liner questions require a model answer.');
        return;
      }
    }

    try {
      setSavingQuestion(true);
      const payload = {
        questionText: qText.trim(),
        questionType: qType,
        marks: Number(qMarks),
        orderIndex: (assessment?.questions?.length || 0) + 1,
        modelAnswer: qType !== 'MCQ' ? qModelAnswer.trim() : undefined,
        rubric: qType !== 'MCQ' ? qRubric.trim() : undefined,
        options:
          qType === 'MCQ'
            ? mcqOptions
                .filter((o) => o.text.trim().length > 0)
                .map((o, idx) => ({
                  optionText: o.text.trim(),
                  isCorrect: o.isCorrect,
                  orderIndex: idx + 1,
                }))
            : [],
      };

      await api.post(`/assessments/${id}/questions`, payload);
      addToast('Question added successfully', 'success');
      setManualModalOpen(false);
      resetManualForm();
      fetchAssessment();
    } catch (err) {
      setQuestionError(err.response?.data?.message || 'Failed to save question.');
    } finally {
      setSavingQuestion(false);
    }
  };

  const resetManualForm = () => {
    setQText('');
    setQType('MCQ');
    setQMarks(1);
    setQModelAnswer('');
    setQRubric('');
    setMcqOptions([
      { text: '', isCorrect: true },
      { text: '', isCorrect: false },
      { text: '', isCorrect: false },
      { text: '', isCorrect: false },
    ]);
  };

  const isDraft = assessment?.status === 'DRAFT';

  if (loading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-28 rounded-xl" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  if (error || !assessment) {
    return <ErrorState message={error || 'Assessment not found'} onRetry={fetchAssessment} />;
  }

  return (
    <div className="space-y-6">
      {/* Back button */}
      <div>
        <Button variant="secondary" size="sm" onClick={() => navigate('/assessments')}>
          <ArrowLeft className="w-4 h-4 mr-1.5" /> Back to Assessments
        </Button>
      </div>

      {/* Header Card */}
      <Card className="space-y-4">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-slate-100">
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700">
                {assessment.classroomTitle}
              </span>
              <span
                className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                  isDraft
                    ? 'bg-amber-100 text-amber-800'
                    : assessment.status === 'PUBLISHED'
                    ? 'bg-emerald-100 text-emerald-800'
                    : 'bg-slate-100 text-slate-700'
                }`}
              >
                {assessment.status}
              </span>
            </div>
            <h1 className="text-2xl font-bold text-slate-900 mt-1">{assessment.title}</h1>
            {assessment.description && (
              <p className="text-sm text-slate-600 mt-1">{assessment.description}</p>
            )}
          </div>

          {/* Action buttons */}
          <div className="flex flex-wrap items-center gap-2">
            {isDraft ? (
              <>
                <Button
                  variant="secondary"
                  onClick={() => setAiModalOpen(true)}
                >
                  <Sparkles className="w-4 h-4 mr-2 text-indigo-600" />
                  Generate with AI
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => {
                    resetManualForm();
                    setManualModalOpen(true);
                  }}
                >
                  <Plus className="w-4 h-4 mr-2" />
                  Add Question
                </Button>
                <Button
                  variant="primary"
                  onClick={handlePublish}
                  disabled={publishing || assessment.questions.length === 0}
                >
                  <Send className="w-4 h-4 mr-2" />
                  {publishing ? 'Publishing...' : 'Publish Assessment'}
                </Button>
              </>
            ) : assessment.status === 'PUBLISHED' ? (
              <>
                <Button variant="secondary" onClick={handleClose}>
                  <Archive className="w-4 h-4 mr-2" /> Close Submissions
                </Button>
                <Button
                  variant="primary"
                  onClick={() => navigate(`/assessments/${id}/attempts`)}
                >
                  View Student Results
                </Button>
              </>
            ) : null}
          </div>
        </div>

        {/* Assessment Meta details */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs text-slate-600 pt-1">
          <div className="flex items-center gap-2">
            <Clock className="w-4 h-4 text-slate-400" />
            <span>Duration: <strong>{assessment.durationMinutes} mins</strong></span>
          </div>
          <div className="flex items-center gap-2">
            <Award className="w-4 h-4 text-slate-400" />
            <span>Total Marks: <strong>{assessment.totalMarks}</strong></span>
          </div>
          <div className="flex items-center gap-2">
            <FileText className="w-4 h-4 text-slate-400" />
            <span>Questions: <strong>{assessment.questions.length}</strong></span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle className="w-4 h-4 text-slate-400" />
            <span>Max Attempts: <strong>{assessment.maxAttempts}</strong></span>
          </div>
        </div>
      </Card>

      {/* Questions Section */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-slate-900">
            Questions ({assessment.questions.length})
          </h2>
          {isDraft && assessment.questions.length === 0 && (
            <span className="text-xs text-amber-600 flex items-center gap-1">
              <AlertTriangle className="w-3.5 h-3.5" /> At least 1 question required to publish
            </span>
          )}
        </div>

        {assessment.questions.length === 0 ? (
          <EmptyState
            title="No Questions Added"
            description="Use the AI Question Generator to create questions from classroom PDFs, or add questions manually."
            icon={HelpCircle}
            actionLabel={isDraft ? "Generate with AI" : undefined}
            onAction={isDraft ? () => setAiModalOpen(true) : undefined}
          />
        ) : (
          <div className="space-y-3">
            {assessment.questions.map((q, idx) => (
              <Card key={q.id} className="p-4 space-y-3 hover:border-slate-300 transition-colors">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-2">
                    <span className="w-6 h-6 rounded-full bg-slate-100 text-slate-700 text-xs font-bold flex items-center justify-center">
                      {idx + 1}
                    </span>
                    <span className="px-2 py-0.5 rounded text-xs font-semibold bg-indigo-50 text-indigo-700">
                      {q.questionType}
                    </span>
                    <span className="text-xs font-medium text-slate-500">
                      {q.marks} {Number(q.marks) === 1 ? 'mark' : 'marks'}
                    </span>
                  </div>

                  {isDraft && (
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => handleMoveQuestion(idx, -1)}
                        disabled={idx === 0}
                        className="p-1 text-slate-400 hover:text-slate-700 disabled:opacity-30 rounded"
                        title="Move up"
                      >
                        <ChevronUp className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleMoveQuestion(idx, 1)}
                        disabled={idx === assessment.questions.length - 1}
                        className="p-1 text-slate-400 hover:text-slate-700 disabled:opacity-30 rounded"
                        title="Move down"
                      >
                        <ChevronDown className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleDeleteQuestion(q.id)}
                        className="p-1 text-red-500 hover:text-red-700 rounded ml-1"
                        title="Delete question"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  )}
                </div>

                <p className="text-sm font-medium text-slate-800 leading-relaxed">
                  {q.questionText}
                </p>

                {/* MCQ Options with correct answer highlighted for teacher */}
                {q.questionType === 'MCQ' && q.options && (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-1">
                    {q.options.map((opt) => (
                      <div
                        key={opt.id}
                        className={`flex items-center justify-between p-2.5 rounded-lg text-xs border ${
                          opt.isCorrect
                            ? 'bg-emerald-50 border-emerald-300 text-emerald-900 font-medium'
                            : 'bg-white border-slate-200 text-slate-700'
                        }`}
                      >
                        <span>{opt.optionText}</span>
                        {opt.isCorrect && (
                          <span className="text-[10px] px-1.5 py-0.5 bg-emerald-600 text-white rounded font-bold">
                            CORRECT KEY
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                )}

                {/* Subjective Model answer & Rubric */}
                {q.questionType !== 'MCQ' && (
                  <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs space-y-1.5 text-slate-700">
                    {q.modelAnswer && (
                      <div>
                        <strong className="text-slate-900">Model Answer:</strong> {q.modelAnswer}
                      </div>
                    )}
                    {q.rubric && (
                      <div>
                        <strong className="text-slate-900">Scoring Rubric:</strong> {q.rubric}
                      </div>
                    )}
                  </div>
                )}
              </Card>
            ))}
          </div>
        )}
      </div>

      {/* AI Question Generator Modal */}
      <AiGeneratorModal
        isOpen={aiModalOpen}
        onClose={() => setAiModalOpen(false)}
        classroomId={assessment.classroomId}
        assessmentId={assessment.id}
        onQuestionsApplied={() => {
          addToast('Generated questions successfully attached to assessment!', 'success');
          fetchAssessment();
        }}
      />

      {/* Manual Question Modal */}
      <Modal
        isOpen={manualModalOpen}
        onClose={() => setManualModalOpen(false)}
        title="Add Question Manually"
        maxWidth="max-w-xl"
      >
        <form onSubmit={handleSaveManualQuestion} className="space-y-4">
          {questionError && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-xs text-red-800">
              {questionError}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">
              Question Text <span className="text-red-500">*</span>
            </label>
            <textarea
              value={qText}
              onChange={(e) => setQText(e.target.value)}
              placeholder="Enter full question statement..."
              rows={2}
              required
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Question Type</label>
              <select
                value={qType}
                onChange={(e) => setQType(e.target.value)}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
              >
                <option value="MCQ">Multiple Choice (MCQ)</option>
                <option value="SUBJECTIVE">Subjective Essay</option>
                <option value="ONE_LINER">One-Liner / Short Answer</option>
              </select>
            </div>
            <div>
              <Input
                label="Marks"
                type="number"
                step="0.5"
                min="0.5"
                max="100"
                value={qMarks}
                onChange={(e) => setQMarks(e.target.value)}
                required
              />
            </div>
          </div>

          {/* MCQ Options fields */}
          {qType === 'MCQ' ? (
            <div className="space-y-2 pt-2 border-t border-slate-100">
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Answer Options (Select the correct key)
              </label>
              {mcqOptions.map((opt, idx) => (
                <div key={idx} className="flex items-center gap-2">
                  <input
                    type="radio"
                    name="correctKey"
                    checked={opt.isCorrect}
                    onChange={() => {
                      const updated = mcqOptions.map((o, i) => ({
                        ...o,
                        isCorrect: i === idx,
                      }));
                      setMcqOptions(updated);
                    }}
                    className="w-4 h-4 text-indigo-600 focus:ring-indigo-500"
                  />
                  <input
                    type="text"
                    value={opt.text}
                    onChange={(e) => {
                      const updated = [...mcqOptions];
                      updated[idx].text = e.target.value;
                      setMcqOptions(updated);
                    }}
                    placeholder={`Option ${idx + 1}`}
                    required={idx < 2}
                    className="flex-1 px-3 py-1.5 border border-slate-300 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
              ))}
            </div>
          ) : (
            <div className="space-y-3 pt-2 border-t border-slate-100">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">
                  Model Answer <span className="text-red-500">*</span>
                </label>
                <textarea
                  value={qModelAnswer}
                  onChange={(e) => setQModelAnswer(e.target.value)}
                  placeholder="Expected correct answer for AI and manual grading reference..."
                  rows={2}
                  required
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Scoring Rubric (Optional)</label>
                <textarea
                  value={qRubric}
                  onChange={(e) => setQRubric(e.target.value)}
                  placeholder="Detailed criteria used by AI for partial mark allocation..."
                  rows={2}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-3 border-t border-slate-200">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setManualModalOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={savingQuestion}>
              {savingQuestion ? 'Saving...' : 'Add Question'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
