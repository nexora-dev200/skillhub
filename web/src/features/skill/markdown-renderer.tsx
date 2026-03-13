import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'

interface MarkdownRendererProps {
  content: string
  className?: string
}

export function MarkdownRenderer({ content, className }: MarkdownRendererProps) {
  const containerClassName = [className, 'prose prose-sm max-w-none dark:prose-invert']
    .filter(Boolean)
    .join(' ')

  return (
    <div className={containerClassName}>
      <ReactMarkdown rehypePlugins={[rehypeHighlight]}>{content}</ReactMarkdown>
    </div>
  )
}
