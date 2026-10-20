export type TravelClearanceStatus =
  | 'DRAFT'
  | 'PENDING_SUPERVISOR'
  | 'APPROVED'
  | 'REJECTED'

export interface TravelClearanceRequest {
  id: string
  purpose: string
  destination: string
  travelDate: string
  status: TravelClearanceStatus
  requesterUsername: string
  createdAt: string
  updatedAt: string
}

export interface CreateTravelClearanceRequest {
  purpose: string
  destination: string
  travelDate: string
}
