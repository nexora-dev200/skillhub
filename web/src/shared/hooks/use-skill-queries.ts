import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { SkillSummary, SkillDetail, SkillVersion, SkillFile, SearchParams, PagedResponse, PublishResult, Namespace, NamespaceMember } from '@/api/types'

// Mock API functions - replace with actual API calls
async function searchSkills(params: SearchParams): Promise<PagedResponse<SkillSummary>> {
  // TODO: Replace with actual API call
  console.log('searchSkills', params)
  return {
    items: [],
    total: 0,
    page: params.page || 1,
    size: params.size || 10,
  }
}

async function getSkillDetail(_namespace: string, _slug: string): Promise<SkillDetail> {
  // TODO: Replace with actual API call
  throw new Error('Not implemented')
}

async function getSkillVersions(_skillId: number): Promise<SkillVersion[]> {
  // TODO: Replace with actual API call
  return []
}

async function getSkillFiles(_versionId: number): Promise<SkillFile[]> {
  // TODO: Replace with actual API call
  return []
}

async function getSkillReadme(_versionId: number): Promise<string> {
  // TODO: Replace with actual API call
  return '# README\n\nNo content available.'
}

async function getMySkills(): Promise<SkillSummary[]> {
  // TODO: Replace with actual API call
  return []
}

async function getMyNamespaces(): Promise<Namespace[]> {
  // TODO: Replace with actual API call
  return []
}

async function getNamespaceDetail(_slug: string): Promise<Namespace> {
  // TODO: Replace with actual API call
  throw new Error('Not implemented')
}

async function getNamespaceMembers(_namespaceId: number): Promise<NamespaceMember[]> {
  // TODO: Replace with actual API call
  return []
}

async function publishSkill(_data: FormData): Promise<PublishResult> {
  // TODO: Replace with actual API call
  throw new Error('Not implemented')
}

// Hooks
export function useSearchSkills(params: SearchParams) {
  return useQuery({
    queryKey: ['skills', 'search', params],
    queryFn: () => searchSkills(params),
  })
}

export function useSkillDetail(namespace: string, slug: string) {
  return useQuery({
    queryKey: ['skills', namespace, slug],
    queryFn: () => getSkillDetail(namespace, slug),
    enabled: !!namespace && !!slug,
  })
}

export function useSkillVersions(skillId: number) {
  return useQuery({
    queryKey: ['skills', skillId, 'versions'],
    queryFn: () => getSkillVersions(skillId),
    enabled: !!skillId,
  })
}

export function useSkillFiles(versionId: number) {
  return useQuery({
    queryKey: ['skills', 'versions', versionId, 'files'],
    queryFn: () => getSkillFiles(versionId),
    enabled: !!versionId,
  })
}

export function useSkillReadme(versionId: number) {
  return useQuery({
    queryKey: ['skills', 'versions', versionId, 'readme'],
    queryFn: () => getSkillReadme(versionId),
    enabled: !!versionId,
  })
}

export function useMySkills() {
  return useQuery({
    queryKey: ['skills', 'my'],
    queryFn: getMySkills,
  })
}

export function useMyNamespaces() {
  return useQuery({
    queryKey: ['namespaces', 'my'],
    queryFn: getMyNamespaces,
  })
}

export function useNamespaceDetail(slug: string) {
  return useQuery({
    queryKey: ['namespaces', slug],
    queryFn: () => getNamespaceDetail(slug),
    enabled: !!slug,
  })
}

export function useNamespaceMembers(namespaceId: number) {
  return useQuery({
    queryKey: ['namespaces', namespaceId, 'members'],
    queryFn: () => getNamespaceMembers(namespaceId),
    enabled: !!namespaceId,
  })
}

export function usePublishSkill() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: publishSkill,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skills', 'my'] })
    },
  })
}
