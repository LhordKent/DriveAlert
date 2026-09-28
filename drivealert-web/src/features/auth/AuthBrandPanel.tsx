import { Brand } from '../../components/Brand'

export function AuthBrandPanel() {
  return (
    <section className="hidden border-r border-border-soft bg-raised p-10 lg:sticky lg:top-0 lg:flex lg:h-screen lg:self-start lg:flex-col xl:p-14">
      <Brand />
      <div className="flex flex-1 items-center py-12 xl:py-16">
        <div className="max-w-[34rem]">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#F64B56]">Trusted Contact Access</p>
          <h2 className="mt-5 max-w-[31rem] text-[2.5rem] font-bold leading-[1.12] tracking-[-0.035em]">Stay informed when important driver alerts are shared.</h2>
          <p className="mt-6 max-w-[32rem] text-base leading-7 text-secondary">Sign in to review alert records shared by approved Drivers, including important drowsiness-related events and recent history. DriveAlert supports awareness and follow-up only after records are shared.</p>
          <p className="mt-8 max-w-[32rem] border-t border-border-soft pt-5 text-sm font-medium leading-6 text-muted">No live tracking. No emergency dispatch. No medical diagnosis.</p>
        </div>
      </div>
    </section>
  )
}
