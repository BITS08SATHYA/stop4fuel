package com.stopforfuel.app.data.repository

import com.stopforfuel.app.data.remote.ApiService
import com.stopforfuel.app.data.remote.dto.ApprovalPendingCountDto
import com.stopforfuel.app.data.remote.dto.ApprovalRequestDto
import com.stopforfuel.app.data.remote.dto.ApprovalReviewBody
import com.stopforfuel.app.data.remote.dto.SubmitApprovalRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApprovalRequestRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun submit(body: SubmitApprovalRequestBody): Result<ApprovalRequestDto> = runCatching {
        api.submitApprovalRequest(body)
    }

    suspend fun listMine(): Result<List<ApprovalRequestDto>> = runCatching {
        api.getMyApprovalRequests()
    }

    suspend fun listPending(): Result<List<ApprovalRequestDto>> = runCatching {
        api.getPendingApprovalRequests()
    }

    suspend fun pendingCount(): Result<ApprovalPendingCountDto> = runCatching {
        api.getPendingApprovalCount()
    }

    suspend fun approve(id: Long, note: String?): Result<ApprovalRequestDto> = runCatching {
        api.approveApprovalRequest(id, ApprovalReviewBody(note))
    }

    suspend fun reject(id: Long, note: String): Result<ApprovalRequestDto> = runCatching {
        api.rejectApprovalRequest(id, ApprovalReviewBody(note))
    }
}
