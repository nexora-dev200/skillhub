import { useCallback } from 'react'
import { useDropzone } from 'react-dropzone'
import { cn } from '@/shared/lib/utils'

interface UploadZoneProps {
  onFileSelect: (file: File) => void
  disabled?: boolean
}

export function UploadZone({ onFileSelect, disabled }: UploadZoneProps) {
  const onDrop = useCallback(
    (acceptedFiles: File[]) => {
      if (acceptedFiles.length > 0) {
        onFileSelect(acceptedFiles[0])
      }
    },
    [onFileSelect]
  )

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      'application/zip': ['.zip'],
    },
    maxFiles: 1,
    disabled,
  })

  return (
    <div
      {...getRootProps()}
      className={cn(
        'border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors',
        isDragActive && 'border-primary bg-primary/5',
        !isDragActive && 'border-muted-foreground/25 hover:border-primary/50',
        disabled && 'opacity-50 cursor-not-allowed'
      )}
    >
      <input {...getInputProps()} />
      <div className="flex flex-col items-center gap-2">
        <svg
          className="w-12 h-12 text-muted-foreground"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
          />
        </svg>
        {isDragActive ? (
          <p className="text-sm text-muted-foreground">放开以上传文件...</p>
        ) : (
          <>
            <p className="text-sm font-medium">拖拽 ZIP 文件到此处，或点击选择</p>
            <p className="text-xs text-muted-foreground">仅支持 .zip 格式</p>
          </>
        )}
      </div>
    </div>
  )
}
