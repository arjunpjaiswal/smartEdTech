import React from 'react';
import { AlertCircle, RotateCcw } from 'lucide-react';
import { Button } from './Button';

export const ErrorState = ({
  title = 'Something went wrong',
  message = 'An unexpected error occurred while loading this content.',
  onRetry,
  className = '',
}) => {
  return (
    <div className={`rounded-xl border border-rose-200 bg-rose-50/50 p-6 text-center ${className}`}>
      <div className="mx-auto w-12 h-12 rounded-full bg-rose-100 flex items-center justify-center text-rose-600 mb-3">
        <AlertCircle className="w-6 h-6" />
      </div>
      <h3 className="text-base font-semibold text-rose-900">{title}</h3>
      <p className="mt-1 text-sm text-rose-700 max-w-md mx-auto">{message}</p>
      {onRetry && (
        <div className="mt-4">
          <Button
            variant="secondary"
            size="sm"
            onClick={onRetry}
            icon={RotateCcw}
            className="border-rose-300 text-rose-700 hover:bg-rose-100/50"
          >
            Try Again
          </Button>
        </div>
      )}
    </div>
  );
};
