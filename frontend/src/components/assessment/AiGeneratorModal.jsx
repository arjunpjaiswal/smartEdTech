import React, { useState, useEffect, useRef } from 'react';
import { api } from '../../api/client';
import { Modal } from '../common/Modal';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import { ErrorState } from '../common/ErrorState';
import { Sparkles, Loader2, RefreshCw, CheckCircle, AlertCircle, FileText } from 'lucide-react';

export const AiGeneratorModal = ({ isOpen, onClose, classroomId, assessmentId, onQuestionsApplied }) => {
  const [materials, setMaterials] = useState([]);
  const [loadingMaterials, setLoadingMaterials] = useState(false);
  const [materialsError, setMaterialsError] = useState(null);
  const [materialId, setMaterialId] = useState('');
  const [count, setCount] = useState(5);
  const [difficulty, setDifficulty] = useState('MEDIUM');
  const [questionTypes, setQuestionTypes] = useState('MIXED');
  const [instructions, setInstructions] = useState('');

  // Generation status: 'IDLE' | 'GENERATING' | 'SUCCESS' | 'ERROR'
  const [genState, setGenState] = useState('IDLE');
  const [jobId, setJobId] = useState(null);
  const [jobError, setJobError] = useState(null);
  const [generatedQuestions, setGeneratedQuestions] = useState([]);
  const [applying, setApplying] = useState(false);

  const pollIntervalRef = useRef(null);

  useEffect(() => {
    if (isOpen && classroomId) {
      fetchMaterials();
    }
    return () => {
      if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
    };
  }, [isOpen, classroomId]);

  const fetchMaterials = async () => {
    try {
      setLoadingMaterials(true);
      setMaterialsError(null);
      const res = await api.get(`/classrooms/${classroomId}/materials`);
      const items = res.data?.content || [];
      setMaterials(items);
      if (items.length > 0 && !materialId) {
        setMaterialId(items[0].id.toString());
      }
    } catch (err) {
      setMaterialsError(err.response?.data?.message || 'Failed to load study materials');
    } finally {
      setLoadingMaterials(false);
    }
  };

  const startPolling = (targetJobId) => {
    if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);

    pollIntervalRef.current = setInterval(async () => {
      try {
        const res = await api.get(`/ai/jobs/${targetJobId}`);
        const job = res.data;

        if (job.status === 'SUCCEEDED') {
          clearInterval(pollIntervalRef.current);
          let parsed = [];
          if (job.resultJson) {
            const data = JSON.parse(job.resultJson);
            parsed = data.questions || [];
          }
          setGeneratedQuestions(parsed);
          setGenState('SUCCESS');
        } else if (job.status === 'FAILED') {
          clearInterval(pollIntervalRef.current);
          setJobError({
            code: job.errorCode || 'GENERATION_FAILED',
            message: job.errorMessage || 'AI question generation encountered an error.',
          });
          setGenState('ERROR');
        }
      } catch (err) {
        clearInterval(pollIntervalRef.current);
        setJobError({
          code: 'NETWORK_ERROR',
          message: err.response?.data?.message || 'Failed to poll generation job status.',
        });
        setGenState('ERROR');
      }
    }, 2000);
  };

  const handleStartGeneration = async (e) => {
    e.preventDefault();
    if (!materialId) return;

    try {
      setGenState('GENERATING');
      setJobError(null);

      const res = await api.post('/ai/generate-questions', {
        materialId: Number(materialId),
        count: Number(count),
        difficulty,
        questionTypes,
        instructions: instructions.trim() || undefined,
      });

      const newJobId = res.data.jobId;
      setJobId(newJobId);
      startPolling(newJobId);
    } catch (err) {
      setJobError({
        code: err.response?.data?.code || 'SUBMISSION_ERROR',
        message: err.response?.data?.message || 'Failed to start AI generation job.',
      });
      setGenState('ERROR');
    }
  };

  const handleRetry = async () => {
    if (!jobId) {
      setGenState('IDLE');
      return;
    }
    try {
      setGenState('GENERATING');
      setJobError(null);
      await api.post(`/ai/jobs/${jobId}/retry`);
      startPolling(jobId);
    } catch (err) {
      setJobError({
        code: err.response?.data?.code || 'RETRY_ERROR',
        message: err.response?.data?.message || 'Failed to retry generation job.',
      });
      setGenState('ERROR');
    }
  };

  const handleApplyQuestions = async () => {
    if (!assessmentId || generatedQuestions.length === 0) return;
    try {
      setApplying(true);
      const res = await api.post(`/assessments/${assessmentId}/apply-generated-questions`, {
        questions: generatedQuestions,
      });
      if (onQuestionsApplied) {
        onQuestionsApplied(res.data);
      }
      handleClose();
    } catch (err) {
      setJobError({
        code: err.response?.data?.code || 'APPLY_ERROR',
        message: err.response?.data?.message || 'Failed to apply questions to assessment.',
      });
    } finally {
      setApplying(false);
    }
  };

  const handleClose = () => {
    if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
    setGenState('IDLE');
    setJobId(null);
    setJobError(null);
    setGeneratedQuestions([]);
    onClose();
  };

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="AI Question Generator" maxWidth="max-w-2xl">
      {genState === 'IDLE' && (
        <form onSubmit={handleStartGeneration} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">
              Source Study Material <span className="text-red-500">*</span>
            </label>
            {loadingMaterials ? (
              <div className="text-xs text-slate-500 flex items-center gap-2 py-2">
                <Loader2 className="w-4 h-4 animate-spin text-indigo-600" /> Loading materials...
              </div>
            ) : materialsError ? (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700 flex items-center justify-between">
                <span>{materialsError}</span>
                <Button size="sm" variant="secondary" onClick={fetchMaterials}>
                  <RefreshCw className="w-3.5 h-3.5 mr-1" /> Retry
                </Button>
              </div>
            ) : materials.length === 0 ? (
              <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-xs text-amber-800">
                No study materials uploaded in this classroom yet. Please upload a PDF or document first.
              </div>
            ) : (
              <select
                value={materialId}
                onChange={(e) => setMaterialId(e.target.value)}
                required
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 bg-white"
              >
                {materials.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.title} ({m.originalFilename})
                  </option>
                ))}
              </select>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <Input
                label="Number of Questions"
                type="number"
                min="1"
                max="30"
                value={count}
                onChange={(e) => setCount(e.target.value)}
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Difficulty</label>
              <select
                value={difficulty}
                onChange={(e) => setDifficulty(e.target.value)}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 bg-white"
              >
                <option value="EASY">Easy</option>
                <option value="MEDIUM">Medium</option>
                <option value="HARD">Hard</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">Question Type</label>
              <select
                value={questionTypes}
                onChange={(e) => setQuestionTypes(e.target.value)}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 bg-white"
              >
                <option value="MIXED">Mixed (MCQ + Subjective)</option>
                <option value="MCQ">Multiple Choice Only</option>
                <option value="SUBJECTIVE">Subjective Only</option>
                <option value="ONE_LINER">One-Liner Only</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">
              Focus Instructions (Optional)
            </label>
            <textarea
              value={instructions}
              onChange={(e) => setInstructions(e.target.value)}
              placeholder="e.g. Focus on chapter 3 formulas and conceptual understanding"
              rows={2}
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            />
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t border-slate-200">
            <Button type="button" variant="secondary" onClick={handleClose}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              disabled={materials.length === 0 || !materialId}
            >
              <Sparkles className="w-4 h-4 mr-2" />
              Generate Questions
            </Button>
          </div>
        </form>
      )}

      {genState === 'GENERATING' && (
        <div className="py-8 text-center space-y-4">
          <div className="w-14 h-14 bg-indigo-50 border border-indigo-100 rounded-2xl flex items-center justify-center mx-auto text-indigo-600 animate-pulse">
            <Sparkles className="w-7 h-7 animate-spin" />
          </div>
          <div>
            <h4 className="text-base font-semibold text-slate-900">AI Generation in Progress</h4>
            <p className="text-sm text-slate-600 mt-1 max-w-md mx-auto">
              Analyzing source material and generating typed assessment questions with scoring rubrics and topic tags.
            </p>
            {jobId && (
              <span className="inline-block mt-3 px-2.5 py-1 bg-slate-100 text-slate-600 text-xs rounded font-mono">
                Job ID: #{jobId}
              </span>
            )}
          </div>
          <div className="flex justify-center items-center gap-2 text-xs text-slate-400">
            <Loader2 className="w-3.5 h-3.5 animate-spin text-indigo-500" />
            Polling server every 2 seconds...
          </div>
        </div>
      )}

      {genState === 'ERROR' && (
        <div className="space-y-4 py-2">
          <ErrorState
            title="AI Generation Failed"
            message={jobError?.message || 'An error occurred during AI processing.'}
            onRetry={handleRetry}
          />
          {jobError?.code === 'INTEGRATION_NOT_CONFIGURED' && (
            <div className="p-3 bg-blue-50 border border-blue-200 rounded-lg text-xs text-blue-900 space-y-1">
              <div className="font-semibold">Setup instructions:</div>
              <div>Configure <code>GEMINI_API_KEY</code> or <code>GROQ_API_KEY</code> in your environment to enable AI assessment generation.</div>
            </div>
          )}
          <div className="flex justify-between items-center pt-3 border-t border-slate-200">
            <Button variant="secondary" onClick={() => setGenState('IDLE')}>
              Back to Settings
            </Button>
            <Button variant="primary" onClick={handleRetry}>
              <RefreshCw className="w-4 h-4 mr-2" />
              Retry Generation
            </Button>
          </div>
        </div>
      )}

      {genState === 'SUCCESS' && (
        <div className="space-y-4">
          <div className="flex items-center gap-2 p-3 bg-emerald-50 border border-emerald-200 rounded-lg text-emerald-800 text-sm">
            <CheckCircle className="w-5 h-5 flex-shrink-0 text-emerald-600" />
            <span>
              Successfully generated <strong>{generatedQuestions.length}</strong> questions with verified schema and answer keys.
            </span>
          </div>

          <div className="max-h-72 overflow-y-auto space-y-3 pr-1">
            {generatedQuestions.map((q, idx) => (
              <div key={idx} className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs space-y-1.5">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-slate-800">
                    Q{idx + 1}: {q.questionText}
                  </span>
                  <span className="px-2 py-0.5 bg-indigo-100 text-indigo-700 rounded font-medium">
                    {q.questionType} ({q.marks} pts)
                  </span>
                </div>
                {q.topicTags && q.topicTags.length > 0 && (
                  <div className="flex flex-wrap gap-1">
                    {q.topicTags.map((tag, tIdx) => (
                      <span key={tIdx} className="px-1.5 py-0.5 bg-slate-200 text-slate-700 rounded text-[10px]">
                        {tag}
                      </span>
                    ))}
                  </div>
                )}
                {q.questionType === 'MCQ' && q.options && (
                  <div className="grid grid-cols-2 gap-1 pt-1">
                    {q.options.map((opt, oIdx) => (
                      <div
                        key={oIdx}
                        className={`px-2 py-1 rounded text-[11px] ${
                          opt.isCorrect ? 'bg-emerald-100 text-emerald-800 font-medium' : 'bg-white border text-slate-600'
                        }`}
                      >
                        {opt.optionText} {opt.isCorrect && '✓'}
                      </div>
                    ))}
                  </div>
                )}
                {q.modelAnswer && (
                  <div className="text-slate-600 italic">
                    <span className="font-semibold">Model Answer:</span> {q.modelAnswer}
                  </div>
                )}
              </div>
            ))}
          </div>

          <div className="flex justify-between items-center pt-3 border-t border-slate-200">
            <Button variant="secondary" onClick={() => setGenState('IDLE')}>
              Regenerate
            </Button>
            <Button variant="primary" onClick={handleApplyQuestions} disabled={applying}>
              {applying ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" /> Adding...
                </>
              ) : (
                `Add ${generatedQuestions.length} Questions to Assessment`
              )}
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
};
