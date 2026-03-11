import ReactMarkdown from 'react-markdown'
import rehypeHighlight from 'rehype-highlight'
import 'highlight.js/styles/github.css'

interface MarkdownRendererProps {
  content: string
  className?: string
}

export function MarkdownRenderer({ content, className }: MarkdownRendererProps) {
  return (
    <div className={className}>
      <ReactMarkdown
        rehypePlugins={[rehypeHighlight]}
        components={{
          // @ts-ignore - react-markdown types issue
          div: ({ node, ...props }) => <div className="prose prose-sm dark:prose-invert max-w-none" {...props} />,
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}
