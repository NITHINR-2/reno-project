import { Outlet } from 'react-router'
import { useAuthUser } from '@/features/auth/useAuth'
import { CartDrawer } from './cart/CartDrawer'
import { CartProvider } from './cart/CartProvider'

/** The /dealer route group: one cart shared by the catalogue and order pages. */
export function DealerLayout() {
  const user = useAuthUser()
  return (
    <CartProvider key={user.id} userId={user.id}>
      <Outlet />
      <CartDrawer />
    </CartProvider>
  )
}
