import { useState, type FormEvent } from 'react'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'

interface SearchBarProps {
  defaultValue?: string
  placeholder?: string
  onSearch?: (query: string) => void
}

export function SearchBar({ defaultValue = '', placeholder = '搜索技能...', onSearch }: SearchBarProps) {
  const [query, setQuery] = useState(defaultValue)

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (onSearch) {
      onSearch(query)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex gap-2">
      <Input
        type="text"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder={placeholder}
        className="flex-1"
      />
      <Button type="submit">搜索</Button>
    </form>
  )
}
