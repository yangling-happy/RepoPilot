export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface DocTask {
  eventId: string
  project: string
  branch: string
  commitId: string
  status: 'PENDING' | 'SUCCESS' | 'FAILED'
  duration: number
}

export interface DocFile {
  project: string
  branch: string
  filePath: string
  commitId: string
  docJson: string
  docMarkdown: string
  updateTime: Date
}

export interface DeployTask {
  taskId: string
  project: string
  branch: string
  scriptName: string
  args: string[]
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  operator: string
  startTime: Date
  endTime: Date
  result?: string
}
