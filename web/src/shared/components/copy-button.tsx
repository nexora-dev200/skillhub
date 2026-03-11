import { useState } from 'react'
import { Button } from '@/shared/ui/button'

interface CopyButtonProps {
  text: string
  className?: string
}

export function CopyButton({ text, className }: CopyButtonProps) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }

  return (
    <Button
      variant="outline"
      size="sm"
      onClick={handleCopy}
      className={className}
    >
      {copied ? '已复制' : '复制'}
    </Button>
  )
}
