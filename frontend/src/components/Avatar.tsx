import { useAuth } from "../context/Auth";
export function Avatar({ large = false }: { large?: boolean }) {
  const { user, picture } = useAuth();
  return (
    <span className={`avatar${large ? " large" : ""}`}>
      {picture ? (
        <img src={picture} alt={`${user?.firstName} ${user?.lastName}`} />
      ) : (
        <>
          {user?.firstName[0]}
          {user?.lastName[0]}
        </>
      )}
    </span>
  );
}
