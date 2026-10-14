import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../api/client';
import { Card } from '../components/common/Card';
import { Button } from '../components/common/Button';
import { Modal } from '../components/common/Modal';
import { Input } from '../components/common/Input';
import { ErrorState } from '../components/common/ErrorState';
import { Skeleton } from '../components/common/Skeleton';
import { useToast } from '../components/common/Toast';
import {
  Award,
  CheckCircle,
  XCircle,
  AlertCircle,
  Sparkles,
  ArrowLeft,
  Edit2,
  BookOpen,
  Target,
  ListChecks,
  RefreshCw,
  Clock,
  User,
} from 'lucide-react';

export const AssessmentResultPage = () => {
  const { attemptId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { addToast } = useToast();

  const [attempt, setAttempt] = useState(null);
  const [assessment, setAssessment] = useState(null);
  const [weakConcepts, setWeakConcepts] = useState([]);
  const [weakConceptsError, setWeakConceptsError] = useState(null);
  const [remediationPlan, setRemediationPlan] = useState(null);
  const [remediationError, setRemediationError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Teacher evaluation edit modal
  const [editEvalModalOpen, setEditEvalModalOpen] = useState(false);
  const [editingEval, setEditingEval] = useState(null);
  const [editScore, setEditScore] = useState('');
  const [editFeedback, setEditFeedback] = useState('');
  const [editStrengths, setEditStrengths] = useState('');
  const [editMissing, setEditMissing] = useState('');
  const [savingEval, setSavingEval] = useState(false);

  const fetchAttemptData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      setWeakConceptsError(null);
      setRemediationError(null);

      // Fetch attempt
      const attRes = await api.get(`/attempts/${attemptId}`);
      const attData = attRes.data;
      setAttempt(attData);

      // Fetch assessment details
      const assRes = await api.get(`/assessments/${attData.assessmentId}`);
      setAssessment(assRes.data);

      // Fetch weak concepts for this student
      try {
        const wcRes = await api.get(`/students/${attData.studentId}/weak-concepts`);
        setWeakConcepts(wcRes.data || []);
      } catch (e) {
        if (e.response?.status !== 404) {
          setWeakConceptsError(e.response?.data?.message || 'Failed to load weak concepts');
        }
      }

      // Fetch remediation plan
      try {
        const remRes = await api.get(`/attempts/${attemptId}/remediation`);
        setRemediationPlan(remRes.data);
      } catch (e) {
        if (e.response?.status !== 404) {
          setRemediationError(e.response?.data?.message || 'Failed to load remediation plan');
        }
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load attempt results.');
    } finally {
      setLoading(false);
    }
  }, [attemptId]);

  useEffect(() => {
    fetchAttemptData();
  }, [fetchAttemptData]);

  const handleOpenEditEval = (evaluation) => {
    setEditingEval(evaluation);
    setEditScore(evaluation.score.toString());
    setEditFeedback(evaluation.feedback || '');
    setEditStrengths(evaluation.strengths || '');
    setEditMissing(evaluation.missingConcepts || '');
    setEditEvalModalOpen(true);
  };

  const handleSaveEvaluation = async (e) => {
    e.preventDefault();
    if (!editingEval) return;
    try {
      setSavingEval(true);
      await api.put(`/evaluations/${editingEval.id}`, {
        score: Number(editScore),
        feedback: editFeedback.trim() || undefined,
        strengths: editStrengths.trim() || undefined,
        missingConcepts: editMissing.trim() || undefined,
      });
      addToast('Evaluation marks and feedback updated.', 'success');
      setEditEvalModalOpen(false);
      fetchAttemptData();
    } catch (err) {
      addToast(err.response?.data?.message || 'Failed to update evaluation.', 'error');
    } finally {
      setSavingEval(false);
    }
  };

  const isTeacher = user?.role === 'TEACHER' || user?.role === 'ADMIN';

  if (loading) {
    return (
      <div className="space-y-6 max-w-4xl mx-auto py-6">
        <Skeleton className="h-32 rounded-xl" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  if (error || !attempt) {
    return (
      <div className="max-w-2xl mx-auto py-12">
        <ErrorState message={error || 'Attempt not found.'} onRetry={fetchAttemptData} />
      </div>
    );
  }

  const scorePercentage =
    attempt.maxScore > 0 ? Math.round(((attempt.totalScore || 0) / attempt.maxScore) * 100) : 0;

  const hasProvisionalScores =
    attempt.evaluations &&
    attempt.evaluations.some((ev) => ev.status === 'AI_SUGGESTED');

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-12">
      {/* Navigation */}
      <div>
        <Button variant="secondary" size="sm" onClick={() => navigate('/assessments')}>
          <ArrowLeft className="w-4 h-4 mr-1.5" /> Back to Assessments
        </Button>
      </div>

      {/* Overview Score Card */}
      <Card className="p-6">
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
          <div className="space-y-1">
            <span className="text-xs uppercase tracking-wider text-indigo-600 font-bold">
              Assessment Results
            </span>
            <h1 className="text-2xl font-bold text-slate-900">{attempt.assessmentTitle}</h1>
            <div className="flex items-center gap-4 text-xs text-slate-500 pt-1">
              <span className="flex items-center gap-1">
                <User className="w-3.5 h-3.5" /> {attempt.studentName}
              </span>
              <span>Attempt #{attempt.attemptNumber}</span>
              {attempt.submittedAt && (
                <span>
                  Submitted:{' '}
                  {new Date(attempt.submittedAt).toLocaleDateString()}{' '}
                  {new Date(attempt.submittedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
              )}
            </div>
          </div>

          {/* Big Score Box */}
          <div className="flex items-center gap-4 bg-slate-50 p-4 rounded-xl border border-slate-200">
            <div className="text-right">
              <div className="text-3xl font-extrabold text-slate-900 tracking-tight">
                {attempt.totalScore !== null ? attempt.totalScore : '--'}
                <span className="text-lg font-normal text-slate-400"> / {attempt.maxScore}</span>
              </div>
              <div className="text-xs font-semibold text-slate-500">{scorePercentage}% Score</div>
            </div>

            <div
              className={`w-12 h-12 rounded-xl flex items-center justify-center font-bold text-sm ${
                scorePercentage >= 70
                  ? 'bg-emerald-100 text-emerald-800'
                  : scorePercentage >= 50
                  ? 'bg-amber-100 text-amber-800'
                  : 'bg-rose-100 text-rose-800'
              }`}
            >
              <Award className="w-6 h-6" />
            </div>
          </div>
        </div>

        {/* Provisional Banner */}
        {hasProvisionalScores && (
          <div className="mt-4 p-3 bg-amber-50 border border-amber-200 rounded-lg text-xs text-amber-800 flex items-start gap-2">
            <AlertCircle className="w-4 h-4 flex-shrink-0 text-amber-600 mt-0.5" />
            <div>
              <strong>Provisional Score (AI Evaluated):</strong> Subjective questions have been graded by AI with suggested marks. A teacher will review and finalize the score.
            </div>
          </div>
        )}
      </Card>

      {/* Question Breakdown Section */}
      <div className="space-y-4">
        <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
          <ListChecks className="w-5 h-5 text-indigo-600" /> Question Breakdown & Evaluations
        </h2>

        {assessment?.questions?.map((q, idx) => {
          const evalItem = attempt.evaluations?.find((e) => e.questionId === q.id);
          const studentAns = attempt.answers?.find((a) => a.questionId === q.id);
          const isCorrect = evalItem && evalItem.score > 0 && evalItem.score >= evalItem.maxMarks;

          return (
            <Card key={q.id} className="p-5 space-y-3">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-2">
                  <span className="w-6 h-6 rounded-full bg-slate-100 text-slate-700 text-xs font-bold flex items-center justify-center">
                    {idx + 1}
                  </span>
                  <span className="px-2 py-0.5 rounded text-xs font-semibold bg-slate-100 text-slate-700">
                    {q.questionType}
                  </span>
                </div>

                <div className="flex items-center gap-2">
                  {evalItem && (
                    <span
                      className={`px-2 py-0.5 rounded text-xs font-bold ${
                        evalItem.status === 'AI_SUGGESTED'
                          ? 'bg-amber-100 text-amber-800'
                          : evalItem.status === 'OVERRIDDEN' || evalItem.status === 'ACCEPTED'
                          ? 'bg-purple-100 text-purple-800'
                          : isCorrect
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-rose-100 text-rose-800'
                      }`}
                    >
                      {evalItem.score} / {evalItem.maxMarks} pts ({evalItem.status})
                    </span>
                  )}
                  {isTeacher && evalItem && (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => handleOpenEditEval(evalItem)}
                    >
                      <Edit2 className="w-3.5 h-3.5 mr-1" /> Edit Marks
                    </Button>
                  )}
                </div>
              </div>

              <p className="text-sm font-medium text-slate-900">{q.questionText}</p>

              {/* Student answer details */}
              <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs space-y-2">
                <div>
                  <span className="font-semibold text-slate-700">Student Response:</span>{' '}
                  {q.questionType === 'MCQ' ? (
                    (() => {
                      const selectedOpt = q.options?.find((o) => o.id === studentAns?.selectedOptionId);
                      return selectedOpt ? selectedOpt.optionText : <span className="text-slate-400 italic">No option selected</span>;
                    })()
                  ) : (
                    <span className="text-slate-800 italic">
                      {studentAns?.answerText || <span className="text-slate-400">No response provided</span>}
                    </span>
                  )}
                </div>

                {evalItem && evalItem.feedback && (
                  <div className="pt-2 border-t border-slate-200 text-slate-700">
                    <span className="font-semibold text-slate-800">Feedback:</span> {evalItem.feedback}
                  </div>
                )}

                {evalItem?.strengths && (
                  <div className="text-emerald-700">
                    <span className="font-semibold">Strengths:</span> {evalItem.strengths}
                  </div>
                )}

                {evalItem?.missingConcepts && evalItem.missingConcepts !== 'None' && (
                  <div className="text-amber-700">
                    <span className="font-semibold">Missing Concepts:</span> {evalItem.missingConcepts}
                  </div>
                )}
              </div>
            </Card>
          );
        })}
      </div>

      {/* Identified Weak Concepts */}
      {weakConceptsError ? (
        <Card className="p-4 bg-red-50 border-red-200">
          <p className="text-xs text-red-700">{weakConceptsError}</p>
        </Card>
      ) : weakConcepts.length > 0 ? (
        <Card className="p-5 space-y-3">
          <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <Target className="w-5 h-5 text-amber-600" /> Identified Weak Concepts
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
            {weakConcepts.map((wc) => (
              <div
                key={wc.id}
                className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs space-y-1"
              >
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-slate-900">{wc.conceptName}</span>
                  <span
                    className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                      wc.severity === 'HIGH'
                        ? 'bg-rose-100 text-rose-800'
                        : wc.severity === 'MODERATE'
                        ? 'bg-amber-100 text-amber-800'
                        : 'bg-blue-100 text-blue-800'
                    }`}
                  >
                    {wc.severity}
                  </span>
                </div>
                {wc.explanation && (
                  <p className="text-slate-600 leading-relaxed">{wc.explanation}</p>
                )}
              </div>
            ))}
          </div>
        </Card>
      ) : null}

      {/* AI Remediation Plan Section */}
      <Card className="p-6 space-y-4 border-indigo-100 bg-gradient-to-b from-white to-indigo-50/20">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-indigo-600" /> AI Personalized Remediation Plan
          </h2>
          {remediationPlan && (
            <span className="text-xs px-2.5 py-0.5 bg-indigo-100 text-indigo-700 rounded-full font-medium">
              Powered by {remediationPlan.providerUsed}
            </span>
          )}
        </div>

        {remediationError ? (
          <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg text-xs text-rose-700">
            {remediationError}
          </div>
        ) : remediationPlan ? (
          <div className="space-y-4">
            <div className="p-3.5 bg-white border border-slate-200 rounded-xl text-sm text-slate-800 leading-relaxed shadow-sm">
              <h4 className="font-semibold text-xs text-slate-500 uppercase tracking-wider mb-1">
                Executive Summary
              </h4>
              <p>{remediationPlan.summary}</p>
            </div>

            {/* Recommendations */}
            {(() => {
              let recs = [];
              let parseError = false;
              try {
                recs = JSON.parse(remediationPlan.recommendationsJson || '[]');
              } catch (e) {
                parseError = true;
              }

              if (parseError) {
                return (
                  <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg text-xs text-rose-700">
                    Failed to render recommendations: data format is invalid.
                  </div>
                );
              }

              if (!recs || recs.length === 0) return null;

              return (
                <div className="space-y-3">
                  <h4 className="font-semibold text-xs text-slate-700 uppercase tracking-wider">
                    Recommended Study Actions
                  </h4>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {recs.map((rec, rIdx) => (
                      <div
                        key={rIdx}
                        className="p-3.5 bg-white border border-slate-200 rounded-xl space-y-2 text-xs shadow-sm"
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-slate-900">{rec.concept}</span>
                          <span
                            className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                              rec.priority === 'HIGH'
                                ? 'bg-rose-100 text-rose-800'
                                : rec.priority === 'MEDIUM'
                                ? 'bg-amber-100 text-amber-800'
                                : 'bg-slate-100 text-slate-800'
                            }`}
                          >
                            {rec.priority} Priority
                          </span>
                        </div>

                        {rec.actionItems && rec.actionItems.length > 0 && (
                          <div className="space-y-1 pt-1">
                            <span className="font-semibold text-slate-600">Action Items:</span>
                            <ul className="list-disc list-inside space-y-0.5 text-slate-700">
                              {rec.actionItems.map((item, iIdx) => (
                                <li key={iIdx}>{item}</li>
                              ))}
                            </ul>
                          </div>
                        )}

                        {rec.recommendedTopics && rec.recommendedTopics.length > 0 && (
                          <div className="pt-1">
                            <span className="font-semibold text-slate-600">Topics to Review:</span>
                            <div className="flex flex-wrap gap-1 mt-1">
                              {rec.recommendedTopics.map((top, tIdx) => (
                                <span
                                  key={tIdx}
                                  className="px-2 py-0.5 bg-indigo-50 text-indigo-700 rounded text-[10px]"
                                >
                                  {top}
                                </span>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              );
            })()}
          </div>
        ) : (
          <div className="p-4 bg-white border border-slate-200 rounded-xl text-center space-y-2 text-xs text-slate-500">
            <Sparkles className="w-5 h-5 mx-auto text-indigo-400" />
            <p>
              AI remediation plan is currently generating based on the assessment feedback. Refresh in a few moments.
            </p>
            <Button variant="secondary" size="sm" onClick={fetchAttemptData}>
              <RefreshCw className="w-3.5 h-3.5 mr-1" /> Refresh Results
            </Button>
          </div>
        )}
      </Card>

      {/* Teacher Edit Evaluation Modal */}
      <Modal
        isOpen={editEvalModalOpen}
        onClose={() => setEditEvalModalOpen(false)}
        title="Adjust Student Marks & Feedback"
      >
        <form onSubmit={handleSaveEvaluation} className="space-y-4">
          <Input
            label={`Score (Max: ${editingEval?.maxMarks})`}
            type="number"
            step="0.5"
            min="0"
            max={editingEval?.maxMarks}
            value={editScore}
            onChange={(e) => setEditScore(e.target.value)}
            required
          />

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Feedback</label>
            <textarea
              value={editFeedback}
              onChange={(e) => setEditFeedback(e.target.value)}
              rows={2}
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Strengths</label>
            <input
              type="text"
              value={editStrengths}
              onChange={(e) => setEditStrengths(e.target.value)}
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Missing Concepts</label>
            <input
              type="text"
              value={editMissing}
              onChange={(e) => setEditMissing(e.target.value)}
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div className="flex justify-end gap-2 pt-3 border-t border-slate-200">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setEditEvalModalOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={savingEval}>
              {savingEval ? 'Saving...' : 'Update Evaluation'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
