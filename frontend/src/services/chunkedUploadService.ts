import request from '../utils/request';

export interface InitUploadResponse {
  uploadId: string;
  objectKey: string;
  chunkSize: number;
  totalChunks: number;
}

export interface UploadChunkResponse {
  chunkNumber: number;
  uploadedChunks: number;
  totalChunks: number;
}

export interface UploadInfo {
  uploadId: string;
  objectKey: string;
  totalSize: number;
  uploadedChunks: number;
  totalChunks: number;
}

export interface ChunkedUploadOptions {
  file: File;
  objectKey: string;
  chunkSize?: number;
  onProgress?: (uploaded: number, total: number) => void;
  onChunkUploaded?: (chunkNumber: number, totalChunks: number) => void;
}

const DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB
const CHUNK_TIMEOUT_MS = 5 * 60 * 1000;
const COMPLETE_TIMEOUT_MS = 30 * 60 * 1000;

export const chunkedUploadService = {
  async initUpload(objectKey: string, totalSize: number, chunkSize?: number): Promise<InitUploadResponse> {
    const response = await request.post('/upload/init', {
      objectKey,
      totalSize,
      chunkSize: chunkSize || DEFAULT_CHUNK_SIZE,
    });
    return response.data;
  },

  async uploadChunk(uploadId: string, chunkNumber: number, file: Blob): Promise<UploadChunkResponse> {
    const formData = new FormData();
    formData.append('file', file, 'chunk');
    const response = await request.post(
      `/upload/chunk?uploadId=${uploadId}&chunkNumber=${chunkNumber}`,
      formData,
      {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: CHUNK_TIMEOUT_MS,
      }
    );
    return response.data;
  },

  async completeUpload(uploadId: string): Promise<void> {
    await request.post('/upload/complete', { uploadId }, { timeout: COMPLETE_TIMEOUT_MS });
  },

  async getUploadInfo(uploadId: string): Promise<UploadInfo> {
    const response = await request.get(`/upload/info/${uploadId}`);
    return response.data;
  },

  async abortUpload(uploadId: string): Promise<void> {
    await request.delete(`/upload/${uploadId}`);
  },

  async uploadFile(options: ChunkedUploadOptions): Promise<string> {
    const { file, objectKey, chunkSize = DEFAULT_CHUNK_SIZE, onProgress, onChunkUploaded } = options;
    
    // 初始化上传
    const initResponse = await this.initUpload(objectKey, file.size, chunkSize);
    const { uploadId, totalChunks, chunkSize: negotiatedChunkSize } = initResponse;
    
    try {
      // 分片上传
      for (let chunkNumber = 1; chunkNumber <= totalChunks; chunkNumber++) {
        const start = (chunkNumber - 1) * negotiatedChunkSize;
        const end = Math.min(start + negotiatedChunkSize, file.size);
        const chunk = file.slice(start, end);
        
        await this.uploadChunk(uploadId, chunkNumber, chunk);
        
        if (onProgress) {
          onProgress(chunkNumber, totalChunks);
        }
        if (onChunkUploaded) {
          onChunkUploaded(chunkNumber, totalChunks);
        }
      }
      
      // 完成上传
      await this.completeUpload(uploadId);
      
      return initResponse.objectKey;
    } catch (error) {
      // 上传失败，取消上传
      try {
        await this.abortUpload(uploadId);
      } catch (abortError) {
        console.error('Failed to abort upload:', abortError);
      }
      throw error;
    }
  },
};

export default chunkedUploadService;
